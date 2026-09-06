package br.com.sinapse.platform.readmodel.performance;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.sinapse.platform.curriculum.api.SubjectView;
import br.com.sinapse.platform.curriculum.api.TopicView;
import br.com.sinapse.platform.educational.api.ClassroomView;
import br.com.sinapse.platform.identity.internal.domain.Account;
import br.com.sinapse.platform.learningrecord.api.RecallRating;
import br.com.sinapse.platform.planning.api.PlannedSessionView;
import br.com.sinapse.platform.planning.api.StudyPlanView;
import br.com.sinapse.platform.readmodel.api.ClassroomRosterView;
import br.com.sinapse.platform.readmodel.support.QueryCounter.Measured;
import br.com.sinapse.platform.readmodel.support.ReadModelIntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The measurement ADR 0013 asks for before anyone decides to materialise the class list.
 *
 * <p>Section 5 of the prompt and section 3.6 of the API contract both say the same thing: this is
 * the heaviest read in the system, it must be measured with a realistic classroom, and it must
 * not be optimised before the measurement says so. So this test builds forty students with three
 * months of study behind them and a plan whose sessions have fallen due, reads the class list,
 * and reports what it cost.
 *
 * <p><strong>What it asserts is a ceiling, not a target.</strong> The number below is loose on
 * purpose: a wall-clock assertion tight enough to be a benchmark is an assertion that fails on a
 * loaded continuous integration machine and teaches everyone to ignore it. What has to hold is
 * that the read stays in the range where materialising it would be premature, and that it does
 * not sneak back into a per-student query — which is why the statement count is asserted here
 * too, at the size that actually matters.
 *
 * <p>The window is the configured maximum rather than the whole three months: the history is
 * three months deep so that the tables are the size a term produces, and the read asks for what
 * a screen is allowed to ask for.
 */
class ClassroomRosterMeasurementTest extends ReadModelIntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(ClassroomRosterMeasurementTest.class);

    /** A classroom of the size the pilot expects. */
    private static final int STUDENTS = 40;

    /** Sessions per student, which is roughly a term of studying most days. */
    private static final int SESSIONS_PER_STUDENT = 90;

    /** Distinct topics the history is spread over. */
    private static final int TOPICS = 12;

    /**
     * Ceiling, not a target. Well above anything observed and well below the point at which
     * anybody would reach for a materialised view.
     */
    private static final Duration ACCEPTABLE = Duration.ofSeconds(2);

    /** What the class list costs in statements, regardless of the size of the classroom. */
    private static final long EXPECTED_QUERIES = 9;

    @Test
    void aClassroomOfFortyWithATermOfHistoryIsReadInOnePass() {
        Account teacher = teacherAccount();
        ClassroomView classroom = classroomOf(teacher);
        List<TopicView> topics = topics(TOPICS);
        for (int index = 0; index < STUDENTS; index++) {
            populate(teacher, classroom, topics);
        }

        // One reading of the clock for both ends: two would put the span a few microseconds
        // over the configured ceiling and be refused, which is the ceiling working correctly.
        Instant now = clock.instant();
        Instant from = now.minus(Duration.ofDays(29));
        Instant to = now.plus(Duration.ofDays(1));

        // Once to pay for whatever the first read of a table costs, then the measured pass.
        rosters.of(teacher.id(), classroom.id(), from, to);

        Instant startedAt = Instant.now();
        Measured<ClassroomRosterView> measured = queries.measure(
                () -> rosters.of(teacher.id(), classroom.id(), from, to));
        Duration elapsed = Duration.between(startedAt, Instant.now());

        LOG.info("ListaDaTurma measured: {} students, {} sessions each over three months, "
                        + "{} statements, {} ms",
                measured.result().students().size(), SESSIONS_PER_STUDENT, measured.queries(),
                elapsed.toMillis());

        assertThat(measured.result().students()).hasSize(STUDENTS);
        assertThat(measured.result().students()).allSatisfy(student -> {
            assertThat(student.totalMinutes()).isPositive();
            assertThat(student.adherenceRatio()).isNotNull();
        });
        assertThat(measured.queries())
                .as("the whole point of the batch lookups: forty students, still nine statements")
                .isEqualTo(EXPECTED_QUERIES);
        assertThat(elapsed)
                .as("a ceiling loose enough not to fail on a loaded machine, and tight enough "
                        + "that a return to one query per student would break it")
                .isLessThan(ACCEPTABLE);
    }

    /**
     * One student of the classroom: three months of sessions, and a plan already partly due.
     *
     * <p>Written straight to the database. Going through the aggregates would mean ninety
     * transactions per student and a suite nobody runs; and a closed session cannot be moved
     * into the past afterwards, because the trigger that freezes it does not care why.
     */
    private void populate(Account teacher, ClassroomView classroom, List<TopicView> topics) {
        Account student = student();
        enrol(student, teacher, classroom);

        for (int session = 0; session < SESSIONS_PER_STUDENT; session++) {
            TopicView topic = topics.get(session % topics.size());
            studied(student, topic.id(), Duration.ofDays(1 + session), 30 + (session % 30),
                    RecallRating.values()[session % RecallRating.values().length]);
        }

        StudyPlanView plan = planWithDueSessions(student, topics.getFirst().id(), 6, 4);
        List<PlannedSessionView> scheduled = directory.sessionsOfPlan(plan.id());
        for (int index = 0; index < 3; index++) {
            UUID slot = scheduled.get(index).id();
            executed(student, topics.getFirst().id(), slot, Duration.ofDays(2L + index));
        }
    }

    private List<TopicView> topics(int count) {
        SubjectView subject = subject();
        List<TopicView> topics = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            topics.add(topicOf(subject));
        }
        return topics;
    }
}
