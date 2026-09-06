package br.com.sinapse.platform.planning.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.sinapse.platform.curriculum.api.TopicView;
import br.com.sinapse.platform.identity.internal.domain.Account;
import br.com.sinapse.platform.planning.api.PlanStatus;
import br.com.sinapse.platform.planning.api.PlannedSessionView;
import br.com.sinapse.platform.planning.api.StudyPlanView;
import br.com.sinapse.platform.planning.internal.error.UnknownPlanException;
import br.com.sinapse.platform.planning.support.PlanningIntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Storing a plan and replacing it.
 *
 * <p>Nothing here generates anything: the job, the snapshot and the core client are the next
 * step of the build. What is exercised is the shape a produced plan takes, and the
 * supersession that ADR 0007 requires of re-planning.
 */
class StudyPlanIntegrationTest extends PlanningIntegrationTest {

    @Test
    void aStoredPlanIsInForceAndCarriesItsSessions() {
        Account student = student();
        TopicView topic = topic();

        StudyPlanView plan = plan(student, topic.id(), 3);

        assertThat(plan.status()).isEqualTo(PlanStatus.ACTIVE);
        assertThat(plan.isActive()).isTrue();
        assertThat(plan.supersededAt()).isNull();
        assertThat(plan.fitness())
                .as("the metrics come back as the core reported them; normalising them would "
                        + "create a second copy of a model this module does not own")
                .containsEntry("coverage", 0.8);

        assertThat(directory.activePlanOf(student.id()))
                .map(StudyPlanView::id)
                .contains(plan.id());
        assertThat(directory.sessionsOfPlan(plan.id()))
                .extracting(PlannedSessionView::sequenceIndex)
                .containsExactly(0, 1, 2);
    }

    /**
     * Re-planning, which is the whole of section 9.4.
     *
     * <p>A new plan, the previous one moved to superseded and pointing at its successor, and
     * its planned sessions untouched — executed study sessions reference them.
     */
    @Test
    void replanningSupersedesThePreviousPlanAndPreservesItsSessions() {
        Account student = student();
        TopicView topic = topic();
        StudyPlanView first = plan(student, topic.id(), 3);
        List<UUID> originalSessions = directory.sessionsOfPlan(first.id()).stream()
                .map(PlannedSessionView::id)
                .toList();

        StudyPlanView second = plan(student, topic.id(), 2);

        StudyPlanView superseded = directory.plan(first.id()).orElseThrow();
        assertThat(superseded.status()).isEqualTo(PlanStatus.SUPERSEDED);
        assertThat(superseded.supersededAt()).isNotNull();
        assertThat(superseded.supersededByPlanId())
                .as("the chain is experimental data about how often and at what point in the "
                        + "horizon a student re-plans, obtained for nothing")
                .isEqualTo(second.id());

        assertThat(directory.activePlanOf(student.id()))
                .map(StudyPlanView::id)
                .contains(second.id());
        assertThat(planCount(student.id())).isEqualTo(2);
        assertThat(directory.sessionsOfPlan(first.id()))
                .extracting(PlannedSessionView::id)
                .as("executed study sessions carry these identifiers; losing them would leave "
                        + "the evidence pointing at nothing")
                .containsExactlyElementsOf(originalSessions);
    }

    @Test
    void theHistoryComesBackNewestFirstWithTheChainReadable() {
        Account student = student();
        TopicView topic = topic();
        StudyPlanView first = plan(student, topic.id(), 1);
        StudyPlanView second = plan(student, topic.id(), 1);
        StudyPlanView third = plan(student, topic.id(), 1);

        List<StudyPlanView> history = directory.planHistoryOf(student.id());

        assertThat(history).extracting(StudyPlanView::id)
                .containsExactly(third.id(), second.id(), first.id());
        assertThat(history.get(2).supersededByPlanId()).isEqualTo(second.id());
        assertThat(history.get(1).supersededByPlanId()).isEqualTo(third.id());
        assertThat(history.get(0).supersededByPlanId()).isNull();
    }

    @Test
    void oneStudentsPlanDoesNotDisturbAnothers() {
        Account first = student();
        Account second = student();
        TopicView topic = topic();
        StudyPlanView firstPlan = plan(first, topic.id(), 1);
        plan(second, topic.id(), 1);

        assertThat(directory.activePlanOf(first.id()))
                .map(StudyPlanView::id)
                .contains(firstPlan.id());
        assertThat(directory.planHistoryOf(first.id())).hasSize(1);
    }

    @Test
    void theScheduleWindowAnswersFromThePlanInForce() {
        Account student = student();
        TopicView topic = topic();
        StudyPlanView superseded = plan(student, topic.id(), 3);
        StudyPlanView active = plan(student, topic.id(), 2);
        Instant from = clock.instant();
        Instant to = from.plus(Duration.ofDays(7));

        List<PlannedSessionView> scheduled = directory.plannedSessionsOf(student.id(), from, to);

        assertThat(scheduled)
                .as("the agenda is about what there is to do, which is the plan in force")
                .hasSize(2)
                .allSatisfy(session -> assertThat(session.planId()).isEqualTo(active.id()));
        assertThat(directory.sessionsOfPlan(superseded.id()))
                .as("the replaced plan's sessions are still readable, by plan")
                .hasSize(3);
    }

    @Test
    void plannedSessionsAreFoundByIdentifierInOneQuery() {
        Account student = student();
        StudyPlanView plan = plan(student, topic().id(), 3);
        List<UUID> ids = directory.sessionsOfPlan(plan.id()).stream()
                .map(PlannedSessionView::id)
                .toList();
        UUID unknown = UUID.randomUUID();

        assertThat(directory.plannedSessionsByIds(List.of(ids.get(0), ids.get(2), unknown)))
                .as("an executed session carries a planned session identifier and no foreign "
                        + "key; whatever joins the two does it for a whole screen at once")
                .containsOnlyKeys(ids.get(0), ids.get(2));
        assertThat(directory.plannedSessionsByIds(List.of()))
                .as("a day with nothing executed is a real case, and asking the database about "
                        + "nothing is a query that can only return nothing")
                .isEmpty();
    }

    @Test
    void aPlanOfAnotherAccountIsNotReachable() {
        Account owner = student();
        Account other = student();
        StudyPlanView plan = plan(owner, topic().id(), 1);

        assertThatThrownBy(() -> plans.require(other.id(), plan.id()))
                .as("a plan that exists and one belonging to somebody else answer the same way")
                .isInstanceOf(UnknownPlanException.class);
        assertThatThrownBy(() -> plans.require(owner.id(), UUID.randomUUID()))
                .isInstanceOf(UnknownPlanException.class);
    }
}
