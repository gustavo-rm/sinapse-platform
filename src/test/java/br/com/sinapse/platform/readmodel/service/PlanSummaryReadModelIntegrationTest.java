package br.com.sinapse.platform.readmodel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.sinapse.platform.curriculum.api.TopicView;
import br.com.sinapse.platform.identity.internal.domain.Account;
import br.com.sinapse.platform.planning.api.PlanStatus;
import br.com.sinapse.platform.planning.api.PlannedSessionView;
import br.com.sinapse.platform.planning.api.StudyPlanView;
import br.com.sinapse.platform.readmodel.api.PlanSummaryView;
import br.com.sinapse.platform.readmodel.internal.error.NotReadableException;
import br.com.sinapse.platform.readmodel.support.ReadModelIntegrationTest;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** {@code PlanoResumido}: the plan seen whole, and the rule about what counts as missed. */
class PlanSummaryReadModelIntegrationTest extends ReadModelIntegrationTest {

    @Test
    void theTotalAndTheBreakdownAgreeWithTheStoredPlan() {
        Account student = studentReadyToPlan(3);
        StudyPlanView plan = planFor(student);
        List<PlannedSessionView> sessions = directory.sessionsOfPlan(plan.id());

        PlanSummaryView summary = summaries.of(student.id(), plan.id());

        assertThat(summary.planId()).isEqualTo(plan.id());
        assertThat(summary.status()).isEqualTo(PlanStatus.ACTIVE);
        assertThat(summary.supersededByPlanId()).isNull();
        assertThat(summary.totalPlannedMinutes())
                .isEqualTo(sessions.stream().mapToInt(PlannedSessionView::durationMinutes).sum());
        assertThat(summary.bySubject().stream().mapToInt(PlanSummaryView.SubjectPlan::plannedMinutes).sum())
                .as("a breakdown that does not add up to the total is a breakdown of something else")
                .isEqualTo(summary.totalPlannedMinutes());
        assertThat(summary.bySubject()).allSatisfy(subject -> {
            assertThat(subject.subjectName()).isNotBlank();
            assertThat(subject.sessionCount()).isPositive();
        });
    }

    /**
     * The detail the prompt singles out: a session in the future is not a missed one.
     *
     * <p>Every session of a freshly generated plan is ahead of now, so nothing has fallen due and
     * the ratio is absent — not zero, which would read as a student who followed none of it.
     */
    @Test
    void aPlanThatHasNotStartedReportsNoAdherenceRatherThanZero() {
        Account student = studentReadyToPlan(3);
        StudyPlanView plan = planFor(student);

        PlanSummaryView.Adherence adherence = summaries.of(student.id(), plan.id()).adherence();

        assertThat(adherence.plannedElapsed()).isZero();
        assertThat(adherence.executed()).isZero();
        assertThat(adherence.ratio()).isNull();
    }

    /**
     * Two sessions past due, one of them completed.
     *
     * <p>The plan is written directly because a generated one is entirely in the future and no
     * clock a test can set moves a planned session into the past — the row is immutable by
     * trigger.
     */
    @Test
    void onlySessionsAlreadyPastDueCountTowardsAdherence() {
        Account student = student();
        TopicView topic = topic();
        StudyPlanView plan = planWithDueSessions(student, topic.id(), 2, 1);
        List<PlannedSessionView> sessions = directory.sessionsOfPlan(plan.id());

        PlannedSessionView due = sessions.stream()
                .filter(session -> session.scheduledStart().isBefore(clock.instant()))
                .findFirst()
                .orElseThrow();
        executed(student, topic.id(), due.id(), Duration.ofDays(2));

        PlanSummaryView.Adherence adherence = summaries.of(student.id(), plan.id()).adherence();

        assertThat(adherence.plannedElapsed())
                .as("the session scheduled for next week has not been missed")
                .isEqualTo(2);
        assertThat(adherence.executed()).isEqualTo(1);
        assertThat(adherence.ratio()).isEqualTo(0.5);
    }

    /** An abandoned session is not adherence: the student opened it and did not finish. */
    @Test
    void anAbandonedAttemptDoesNotCountAsExecuted() {
        Account student = student();
        TopicView topic = topic();
        StudyPlanView plan = planWithDueSessions(student, topic.id(), 1, 0);
        PlannedSessionView due = directory.sessionsOfPlan(plan.id()).getFirst();
        abandoned(student, topic.id(), due.id(), Duration.ofDays(2));

        PlanSummaryView.Adherence adherence = summaries.of(student.id(), plan.id()).adherence();

        assertThat(adherence.plannedElapsed()).isEqualTo(1);
        assertThat(adherence.executed()).isZero();
        assertThat(adherence.ratio()).isEqualTo(0.0);
    }

    @Test
    void aPlanBelongingToSomebodyElseAnswersAsThoughItDidNotExist() {
        Account owner = studentReadyToPlan(2);
        StudyPlanView plan = planFor(owner);
        Account other = student();

        assertThatThrownBy(() -> summaries.of(other.id(), plan.id()))
                .isInstanceOf(NotReadableException.class);
        assertThatThrownBy(() -> summaries.of(other.id(), UUID.randomUUID()))
                .as("the same answer, so the route cannot be used to find real identifiers")
                .isInstanceOf(NotReadableException.class);
    }
}
