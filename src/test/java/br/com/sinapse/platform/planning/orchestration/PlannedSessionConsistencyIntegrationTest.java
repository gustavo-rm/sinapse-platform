package br.com.sinapse.platform.planning.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.sinapse.platform.curriculum.api.TopicView;
import br.com.sinapse.platform.identity.internal.domain.Account;
import br.com.sinapse.platform.learningrecord.api.RecallRating;
import br.com.sinapse.platform.learningrecord.api.SessionKind;
import br.com.sinapse.platform.learningrecord.api.StudySessionView;
import br.com.sinapse.platform.learningrecord.support.LearningRecordIntegrationTest;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The check that finds references to planned sessions which no longer exist.
 *
 * <p>It is the whole compensation for {@code study_session.planned_session_id} carrying no
 * foreign key, and section 8.4 accepted that cost knowingly. What is asserted here is not
 * only that an orphan is found, but that finding one changes nothing: the evidence stays.
 */
class PlannedSessionConsistencyIntegrationTest extends LearningRecordIntegrationTest {

    @Autowired
    private PlannedSessionConsistencyService consistency;

    @Test
    void aReferenceToAPlannedSessionThatExistsIsNotAnOrphan() {
        Account student = student();
        TopicView topic = topic();
        UUID plannedSessionId = insertPlannedSession(student.id(), topic.id());

        StudySessionView started = sessions.start(student.id(), topic.id(), plannedSessionId,
                SessionKind.STUDY, 50);
        sessions.complete(student.id(), started.id(), RecallRating.GOOD, 45);

        assertThat(consistency.findOrphanReferences()).isEmpty();
    }

    @Test
    void aReferenceToAPlannedSessionThatDoesNotExistIsReportedAndNothingIsDeleted() {
        Account student = student();
        TopicView topic = topic();
        UUID missing = UUID.randomUUID();
        UUID present = insertPlannedSession(student.id(), topic.id());

        StudySessionView orphaned = sessions.start(student.id(), topic.id(), missing,
                SessionKind.STUDY, 50);
        sessions.complete(student.id(), orphaned.id(), RecallRating.HARD, 30);
        StudySessionView sound = sessions.start(student.id(), topic.id(), present,
                SessionKind.STUDY, 50);
        sessions.complete(student.id(), sound.id(), RecallRating.GOOD, 45);

        List<UUID> orphans = consistency.findOrphanReferences();

        assertThat(orphans).containsExactly(missing);
        assertThat(sessionCount(student.id()))
                .as("it reports and does not delete. Removing a study session to tidy a "
                        + "dangling pointer would destroy evidence that a student actually "
                        + "studied, which is worse than the pointer")
                .isEqualTo(2);
        assertThat(columnOf(orphaned.id(), "status", String.class)).isEqualTo("COMPLETED");
    }

    @Test
    void aRecordWithNoPlannedSessionsAtAllIsConsistent() {
        Account student = student();
        recordedSession(student, topic().id(), 120, 40, RecallRating.GOOD);

        assertThat(consistency.findOrphanReferences())
                .as("a self-directed session references nothing, so there is nothing to dangle")
                .isEmpty();
    }

    /**
     * A planned session, written directly.
     *
     * <p>Planning has no services yet; its aggregates and its generation job are the next step
     * of the build. The check is about rows, and these are the rows.
     */
    private UUID insertPlannedSession(UUID accountId, UUID topicId) {
        UUID requestId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID plannedSessionId = UUID.randomUUID();
        LocalDate start = LocalDate.now(clock);

        jdbc.update("""
                insert into plan_generation_request (id, account_id, status, horizon_start,
                                                     horizon_end)
                values (?, ?, 'READY', ?, ?)
                """, requestId, accountId, start, start.plusDays(7));
        jdbc.update("""
                insert into study_plan (id, account_id, generation_request_id, horizon_start,
                                        horizon_end, status)
                values (?, ?, ?, ?, ?, 'ACTIVE')
                """, planId, accountId, requestId, start, start.plusDays(7));
        jdbc.update("""
                insert into planned_session (id, plan_id, topic_id, kind, scheduled_start,
                                             duration_minutes, sequence_index)
                values (?, ?, ?, 'STUDY', now(), 50, 1)
                """, plannedSessionId, planId, topicId);
        return plannedSessionId;
    }
}
