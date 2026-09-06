package br.com.sinapse.platform.planning.internal.service;

import br.com.sinapse.platform.planning.api.AvailabilityWindowView;
import br.com.sinapse.platform.planning.api.PlannedSessionView;
import br.com.sinapse.platform.planning.api.StudyGoalView;
import br.com.sinapse.platform.planning.api.StudyPlanView;
import br.com.sinapse.platform.planning.internal.domain.AvailabilityWindow;
import br.com.sinapse.platform.planning.internal.domain.PlannedSession;
import br.com.sinapse.platform.planning.internal.domain.StudyGoal;
import br.com.sinapse.platform.planning.internal.domain.StudyPlan;

/**
 * Turns the aggregates into the views other modules and clients receive.
 *
 * <p>One place, so that an entity cannot reach a caller by being returned from a method
 * somebody wrote in a hurry. A {@code StudyPlan} handed across the boundary is an immutable
 * aggregate with a JPA identity attached, and the first caller to touch a field on it would
 * meet a trigger instead of a compiler.
 */
final class PlanningViews {

    private PlanningViews() {
    }

    /**
     * @param window window to publish
     * @return its view
     */
    static AvailabilityWindowView of(AvailabilityWindow window) {
        return new AvailabilityWindowView(window.id(), window.dayOfWeek(), window.startTime(),
                window.endTime(), window.effectiveFrom(), window.effectiveUntil());
    }

    /**
     * @param goal goal to publish
     * @return its view
     */
    static StudyGoalView of(StudyGoal goal) {
        return new StudyGoalView(goal.id(), goal.subjectId(), goal.targetDate(), goal.priority(),
                goal.status(), goal.createdAt(), goal.achievedAt());
    }

    /**
     * @param plan plan to publish
     * @return its view
     */
    static StudyPlanView of(StudyPlan plan) {
        return new StudyPlanView(plan.id(), plan.accountId(), plan.generationRequestId(),
                plan.horizonStart(), plan.horizonEnd(), plan.status(), plan.fitness(),
                plan.createdAt(), plan.supersededAt(), plan.supersededByPlanId());
    }

    /**
     * @param session planned session to publish
     * @return its view
     */
    static PlannedSessionView of(PlannedSession session) {
        return new PlannedSessionView(session.id(), session.plan().id(), session.topicId(),
                session.kind(), session.scheduledStart(), session.durationMinutes(),
                session.sequenceIndex());
    }
}
