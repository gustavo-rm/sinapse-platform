package br.com.sinapse.platform.curriculum.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.sinapse.platform.curriculum.api.EdgeProvenance;
import br.com.sinapse.platform.curriculum.api.EdgeStrength;
import br.com.sinapse.platform.curriculum.api.SubjectView;
import br.com.sinapse.platform.curriculum.api.TopicView;
import br.com.sinapse.platform.curriculum.support.CurriculumIntegrationTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Acyclicity, verified where it is enforced.
 *
 * <p>Every write here goes through plain SQL. That is the point: the guarantee belongs to the
 * database, and an assertion made through the service would only prove that the service
 * currently calls it. The trigger exists because application-level validation cannot hold
 * this rule at all under concurrency, so the test that matters most is the one with two real
 * connections.
 */
class PrerequisiteAcyclicityIntegrationTest extends CurriculumIntegrationTest {

    /** Generous: the second transaction only has to wait for the first to release the lock. */
    private static final int TIMEOUT_SECONDS = 30;

    @Autowired
    private DataSource dataSource;

    @Test
    void anEdgeThatWouldCloseACycleIsRejected() {
        SubjectView subject = subject("Cycle");
        TopicView a = topic(subject, "a", 1);
        TopicView b = topic(subject, "b", 2);
        TopicView c = topic(subject, "c", 3);

        insertEdgeDirectly(a.id(), b.id());
        insertEdgeDirectly(b.id(), c.id());

        assertThatThrownBy(() -> insertEdgeDirectly(c.id(), a.id()))
                .as("a cycle reaching the optimisation core produces undefined behaviour, so it "
                        + "is refused at the deepest level available")
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("would introduce a cycle");

        assertThat(edgeCount()).isEqualTo(2);
    }

    @Test
    void aLongerCycleIsAlsoRejected() {
        SubjectView subject = subject("Long cycle");
        List<TopicView> chain = List.of(
                topic(subject, "a", 1), topic(subject, "b", 2), topic(subject, "c", 3),
                topic(subject, "d", 4), topic(subject, "e", 5));

        for (int i = 0; i + 1 < chain.size(); i++) {
            insertEdgeDirectly(chain.get(i).id(), chain.get(i + 1).id());
        }

        assertThatThrownBy(() -> insertEdgeDirectly(chain.getLast().id(), chain.getFirst().id()))
                .as("the recursive query walks the whole chain, not just one hop")
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("would introduce a cycle");
    }

    /**
     * A self-edge is refused, and it is worth recording <em>what</em> refuses it.
     *
     * <p>{@code ck_prerequisite_no_self_edge} looks like the answer and is not the one that
     * fires. PostgreSQL runs {@code BEFORE} row triggers before it evaluates check
     * constraints, so the acyclicity trigger sees the row first and reports the degenerate
     * cycle. The constraint is still worth having: it is what would catch this if the trigger
     * were ever dropped, and it documents the rule in the schema. But nothing reaches it while
     * the trigger is in place, and a test asserting the constraint name would be asserting
     * something that never happens.
     */
    @Test
    void anEdgeFromATopicToItselfIsRejected() {
        SubjectView subject = subject("Self");
        TopicView a = topic(subject, "a", 1);

        assertThatThrownBy(() -> insertEdgeDirectly(a.id(), a.id()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("would introduce a cycle");

        assertThat(edgeCount()).isZero();
    }

    @Test
    void theCheckConstraintRefusesASelfEdgeWhenTheTriggerIsNotThere() {
        SubjectView subject = subject("Self without trigger");
        TopicView a = topic(subject, "a", 1);

        jdbc.execute("alter table topic_prerequisite disable trigger tg_topic_prerequisite_acyclic");
        try {
            assertThatThrownBy(() -> insertEdgeDirectly(a.id(), a.id()))
                    .as("the second line of defence exists and works; it is simply never the "
                            + "one that answers first")
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("ck_prerequisite_no_self_edge");
        } finally {
            jdbc.execute("alter table topic_prerequisite enable trigger tg_topic_prerequisite_acyclic");
        }
    }

    @Test
    void aDuplicateEdgeOnTheSameOrderedPairIsRejected() {
        SubjectView subject = subject("Duplicate");
        TopicView a = topic(subject, "a", 1);
        TopicView b = topic(subject, "b", 2);
        insertEdgeDirectly(a.id(), b.id());

        assertThatThrownBy(() -> insertEdgeDirectly(a.id(), b.id()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_prerequisite_edge");
    }

    @Test
    void theSameTopicsInTheOtherDirectionAreADifferentPairAndStillACycle() {
        SubjectView subject = subject("Reverse");
        TopicView a = topic(subject, "a", 1);
        TopicView b = topic(subject, "b", 2);
        insertEdgeDirectly(a.id(), b.id());

        assertThatThrownBy(() -> insertEdgeDirectly(b.id(), a.id()))
                .as("the unique constraint is on the ordered pair, so what refuses this is the "
                        + "trigger and not the index")
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("would introduce a cycle");
    }

    @Test
    void theTriggerFiresOnUpdateAndNotOnlyOnInsert() {
        SubjectView subject = subject("Update");
        TopicView a = topic(subject, "a", 1);
        TopicView b = topic(subject, "b", 2);
        TopicView c = topic(subject, "c", 3);

        insertEdgeDirectly(a.id(), b.id());
        insertEdgeDirectly(b.id(), c.id());
        UUID movable = UUID.randomUUID();
        jdbc.update(INSERT_EDGE, movable, a.id(), c.id(), EdgeStrength.SOFT.name(),
                EdgeProvenance.TEXTBOOK_ORDER.name());

        // Repointing a -> c into c -> a closes the cycle a -> b -> c -> a.
        assertThatThrownBy(() -> jdbc.update(
                "update topic_prerequisite set prerequisite_topic_id = ?, dependent_topic_id = ? "
                        + "where id = ?", c.id(), a.id(), movable))
                .as("a validation that only ran on insert would be bypassed by every correction, "
                        + "and corrections are the whole point of edges being editable")
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("would introduce a cycle");
    }

    @Test
    void changingOnlyTheStrengthAlsoPassesThroughTheValidation() {
        SubjectView subject = subject("Strength");
        TopicView a = topic(subject, "a", 1);
        TopicView b = topic(subject, "b", 2);
        UUID edgeId = UUID.randomUUID();
        jdbc.update(INSERT_EDGE, edgeId, a.id(), b.id(), EdgeStrength.SOFT.name(),
                EdgeProvenance.TEXTBOOK_ORDER.name());

        int updated = jdbc.update("update topic_prerequisite set strength = ? where id = ?",
                EdgeStrength.HARD.name(), edgeId);

        assertThat(updated)
                .as("it goes through the trigger, and on an acyclic graph the trigger lets it "
                        + "through, which is the behaviour a curator needs")
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select strength from topic_prerequisite where id = ?",
                String.class, edgeId)).isEqualTo("HARD");
    }

    /**
     * Two transactions, each inserting one edge that is fine on its own, which together close a
     * cycle.
     *
     * <p>This is the test the advisory lock exists for, and the only one that says anything
     * about it. Under read committed the two transactions do not see each other's uncommitted
     * rows, so a check made in application code would pass in both and the cycle would land.
     * The trigger takes {@code pg_advisory_xact_lock} before validating, which serialises the
     * two; the second one then evaluates its recursive query against a snapshot that already
     * includes the first one's committed edge — the function is volatile, so each statement
     * inside it takes a fresh snapshot after the lock is acquired — and refuses.
     *
     * <p>Which of the two loses is not determined, and the assertion does not care. What it
     * asserts is that exactly one does.
     */
    @Test
    void twoConcurrentEdgesThatTogetherCloseACycleLeaveExactlyOneCommitted() throws Exception {
        SubjectView subject = subject("Concurrent");
        TopicView a = topic(subject, "a", 1);
        TopicView b = topic(subject, "b", 2);
        TopicView c = topic(subject, "c", 3);
        insertEdgeDirectly(a.id(), b.id());

        CyclicBarrier bothReady = new CyclicBarrier(2);
        ExecutorService threads = Executors.newFixedThreadPool(2);
        try {
            Future<Optional<String>> addingBtoC = threads.submit(insert(bothReady, b.id(), c.id()));
            Future<Optional<String>> addingCtoA = threads.submit(insert(bothReady, c.id(), a.id()));

            Optional<String> firstOutcome = addingBtoC.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            Optional<String> secondOutcome = addingCtoA.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            List<Optional<String>> outcomes = List.of(firstOutcome, secondOutcome);
            assertThat(outcomes.stream().filter(Optional::isEmpty).count())
                    .as("exactly one of the two transactions commits")
                    .isEqualTo(1);
            assertThat(outcomes.stream().filter(Optional::isPresent).map(Optional::get))
                    .as("and the other is refused for the reason we expect, not by chance")
                    .singleElement(org.assertj.core.api.InstanceOfAssertFactories.STRING)
                    .contains("would introduce a cycle");
        } finally {
            threads.shutdownNow();
        }

        assertThat(edgeCount())
                .as("the graph keeps the edge it started with plus the one that won")
                .isEqualTo(2);
    }

    /**
     * One edge insert, on a connection of its own, in a transaction of its own.
     *
     * @return empty when the transaction committed, or the refusal message when it did not
     */
    private Callable<Optional<String>> insert(CyclicBarrier bothReady, UUID prerequisite,
            UUID dependent) {
        return () -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                // Both transactions are open before either writes, so the second one really
                // does meet the lock rather than arriving after the first has finished.
                bothReady.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                try (PreparedStatement statement = connection.prepareStatement(INSERT_EDGE)) {
                    statement.setObject(1, UUID.randomUUID());
                    statement.setObject(2, prerequisite);
                    statement.setObject(3, dependent);
                    statement.setString(4, EdgeStrength.HARD.name());
                    statement.setString(5, EdgeProvenance.CURATED.name());
                    statement.executeUpdate();
                }
                connection.commit();
                return Optional.empty();
            } catch (SQLException refused) {
                // Closing without committing rolls the transaction back, which is what has to
                // happen to the one that loses.
                return Optional.of(String.valueOf(refused.getMessage()));
            }
        };
    }
}
