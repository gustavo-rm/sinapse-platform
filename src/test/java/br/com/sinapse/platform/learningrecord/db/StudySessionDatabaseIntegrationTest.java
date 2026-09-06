package br.com.sinapse.platform.learningrecord.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.sinapse.platform.curriculum.api.TopicView;
import br.com.sinapse.platform.identity.internal.domain.Account;
import br.com.sinapse.platform.learningrecord.support.LearningRecordIntegrationTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

/**
 * The guarantees that belong to the database, checked where they live.
 *
 * <p>Everything here goes through plain SQL. An assertion made through the service would only
 * prove that the service currently checks something; these constraints exist because a
 * service can stop checking, because a mapping can be changed by accident, and because two
 * concurrent requests can both pass a check made in code.
 */
class StudySessionDatabaseIntegrationTest extends LearningRecordIntegrationTest {

    @Test
    void thePartialIndexRefusesASecondSessionInProgress() {
        Account student = student();
        TopicView topic = topic();
        insertInProgress(student.id(), topic.id());

        assertThatThrownBy(() -> insertInProgress(student.id(), topic.id()))
                .as("invariant 1. Two simultaneous sessions would corrupt the duration record, "
                        + "which is the most basic evidence this module holds")
                .isInstanceOf(DuplicateKeyException.class)
                .hasMessageContaining("ux_session_in_progress");

        assertThat(sessionCount(student.id())).isEqualTo(1);
    }

    @Test
    void theIndexAllowsANewSessionOnceTheFirstHasClosed() {
        Account student = student();
        TopicView topic = topic();
        UUID first = insertInProgress(student.id(), topic.id());
        jdbc.update("update study_session set status = 'ABANDONED', ended_at = now() where id = ?",
                first);

        insertInProgress(student.id(), topic.id());

        assertThat(sessionCount(student.id()))
                .as("the index covers sessions in progress, so the history accrues behind it")
                .isEqualTo(2);
    }

    @Test
    void theIndexIsPerAccount() {
        TopicView topic = topic();
        insertInProgress(student().id(), topic.id());

        assertThatCode(() -> insertInProgress(student().id(), topic.id()))
                .as("one student studying does not stop another from studying")
                .doesNotThrowAnyException();
    }

    @Test
    void theTriggerRefusesAnUpdateToAClosedSession() {
        Account student = student();
        UUID session = insertClosed(student.id(), topic().id(), "COMPLETED", "GOOD");

        assertThatThrownBy(() -> jdbc.update(
                "update study_session set recall_rating = 'EASY' where id = ?", session))
                .as("invariant 2. Evidence that can be edited is not evidence, and the trigger "
                        + "is defence against a mapping accident rather than against a caller")
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("is closed and cannot be modified");

        assertThat(columnOf(session, "recall_rating", String.class)).isEqualTo("GOOD");
    }

    @Test
    void theTriggerAllowsAnUpdateWhileTheSessionIsRunning() {
        Account student = student();
        UUID session = insertInProgress(student.id(), topic().id());

        jdbc.update("update study_session set status = 'COMPLETED', ended_at = now(), "
                + "actual_duration_minutes = 40, duration_source = 'MEASURED', "
                + "recall_rating = 'GOOD' where id = ?", session);

        assertThat(columnOf(session, "status", String.class))
                .as("closing a session is the one update it ever receives")
                .isEqualTo("COMPLETED");
    }

    @Test
    void theTriggerRefusesADeleteOfAnySession() {
        Account student = student();
        UUID running = insertInProgress(student.id(), topic().id());
        UUID closed = insertClosed(student().id(), topic().id(), "ABANDONED", null);

        assertThatThrownBy(() -> jdbc.update("delete from study_session where id = ?", running))
                .as("invariant 3. A session is never deleted, in any state")
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("cannot be deleted");
        assertThatThrownBy(() -> jdbc.update("delete from study_session where id = ?", closed))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("cannot be deleted");

        assertThat(jdbc.queryForObject("select count(*) from study_session", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void aRatingOutsideACompletedSessionIsRefused() {
        Account student = student();
        UUID topicId = topic().id();

        assertThatThrownBy(() -> insertClosed(student.id(), topicId, "ABANDONED", "GOOD"))
                .as("invariant 4. A student who gave up did not judge their recall, and a "
                        + "rating on that record would be a number nobody produced")
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_session_recall_on_completion");

        assertThatThrownBy(() -> jdbc.update(INSERT_SESSION, UUID.randomUUID(), student.id(),
                topicId, null, "STUDY", "SELF_DIRECTED", "IN_PROGRESS", now(), null, null, null,
                null, "GOOD"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_session_recall_on_completion");
    }

    @Test
    void aPlannedSourceWithoutAPlannedSessionIsRefusedAndSoIsTheReverse() {
        Account student = student();
        UUID topicId = topic().id();

        assertThatThrownBy(() -> jdbc.update(INSERT_SESSION, UUID.randomUUID(), student.id(),
                topicId, null, "STUDY", "FROM_PLAN", "IN_PROGRESS", now(), null, null, null, null,
                null))
                .as("invariant 5, one direction")
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_session_source_consistency");

        assertThatThrownBy(() -> jdbc.update(INSERT_SESSION, UUID.randomUUID(), student.id(),
                topicId, UUID.randomUUID(), "STUDY", "SELF_DIRECTED", "IN_PROGRESS", now(), null,
                null, null, null, null))
                .as("invariant 5, the other direction")
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_session_source_consistency");
    }

    /**
     * The reference that has no foreign key, which is the whole of section 8.4.
     *
     * <p>It has to be accepted, because refusing it would mean this table knows what a planned
     * session is. The consistency check is what finds the ones that dangle.
     */
    @Test
    void aReferenceToAPlannedSessionThatDoesNotExistIsAccepted() {
        Account student = student();

        assertThatCode(() -> jdbc.update(INSERT_SESSION, UUID.randomUUID(), student.id(),
                topic().id(), UUID.randomUUID(), "STUDY", "FROM_PLAN", "IN_PROGRESS", now(), null,
                50, null, null, null))
                .as("rule R2 costs exactly this: the database cannot check the reference, "
                        + "because checking it would be a dependency between the two modules")
                .doesNotThrowAnyException();
    }

    @Test
    void aDurationWithoutASourceIsRefusedAndSoIsTheReverse() {
        Account student = student();
        UUID topicId = topic().id();

        assertThatThrownBy(() -> jdbc.update(INSERT_SESSION, UUID.randomUUID(), student.id(),
                topicId, null, "STUDY", "SELF_DIRECTED", "COMPLETED", now(), now(), null, 40, null,
                "GOOD"))
                .as("decision F5 is only worth anything if a duration can never arrive "
                        + "unattributed")
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_session_duration_source_required");

        assertThatThrownBy(() -> jdbc.update(INSERT_SESSION, UUID.randomUUID(), student.id(),
                topicId, null, "STUDY", "SELF_DIRECTED", "COMPLETED", now(), now(), null, null,
                "MEASURED", "GOOD"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_session_duration_source_required");
    }

    @Test
    void aSessionThatEndsBeforeItStartedIsRefused() {
        Account student = student();
        Instant start = clock.instant();

        assertThatThrownBy(() -> jdbc.update(INSERT_SESSION, UUID.randomUUID(), student.id(),
                topic().id(), null, "STUDY", "SELF_DIRECTED", "ABANDONED",
                Timestamp.from(start), Timestamp.from(start.minusSeconds(60)), null, null, null,
                null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_session_order");
    }

    @Test
    void aRunningSessionCannotCarryAnEndOrADuration() {
        Account student = student();

        assertThatThrownBy(() -> jdbc.update(INSERT_SESSION, UUID.randomUUID(), student.id(),
                topic().id(), null, "STUDY", "SELF_DIRECTED", "IN_PROGRESS", now(), now(), null,
                null, null, null))
                .as("a session that has ended is not in progress, whatever the status column "
                        + "was set to")
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_session_closed");
    }

    private UUID insertInProgress(UUID accountId, UUID topicId) {
        UUID id = UUID.randomUUID();
        jdbc.update(INSERT_SESSION, id, accountId, topicId, null, "STUDY", "SELF_DIRECTED",
                "IN_PROGRESS", now(), null, 50, null, null, null);
        return id;
    }

    private UUID insertClosed(UUID accountId, UUID topicId, String status, String rating) {
        UUID id = UUID.randomUUID();
        Instant start = clock.instant().minusSeconds(3600);
        jdbc.update(INSERT_SESSION, id, accountId, topicId, null, "STUDY", "SELF_DIRECTED", status,
                Timestamp.from(start), Timestamp.from(start.plusSeconds(3000)), 50, 50, "MEASURED",
                rating);
        return id;
    }

    private Timestamp now() {
        return Timestamp.from(clock.instant());
    }
}
