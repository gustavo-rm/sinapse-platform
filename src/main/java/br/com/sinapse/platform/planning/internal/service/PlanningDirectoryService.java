package br.com.sinapse.platform.planning.internal.service;

import br.com.sinapse.platform.planning.api.AvailabilityWindowView;
import br.com.sinapse.platform.planning.api.GoalStatus;
import br.com.sinapse.platform.planning.api.PlanStatus;
import br.com.sinapse.platform.planning.api.PlannedSessionView;
import br.com.sinapse.platform.planning.api.PlanningDirectory;
import br.com.sinapse.platform.planning.api.StudyGoalView;
import br.com.sinapse.platform.planning.api.StudyPlanView;
import br.com.sinapse.platform.planning.internal.config.PlanningProperties;
import br.com.sinapse.platform.planning.internal.domain.PlannedSession;
import br.com.sinapse.platform.planning.internal.persistence.AvailabilityWindowRepository;
import br.com.sinapse.platform.planning.internal.persistence.PlannedSessionRepository;
import br.com.sinapse.platform.planning.internal.persistence.StudyGoalRepository;
import br.com.sinapse.platform.planning.internal.persistence.StudyPlanRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The reads this module publishes.
 *
 * <p>Read-only throughout, and every method hands back a DTO. Whether the caller may see the
 * student is settled before this is reached — see {@link PlanningDirectory} — so nothing here
 * filters by who is asking.
 *
 * <p>An empty set of identifiers is answered without going to the database. It is a real case:
 * a day with no executed sessions to match against a plan. Sending it as an {@code in ()} would
 * be a query that can only return nothing.
 */
@Service
@Transactional(readOnly = true)
public class PlanningDirectoryService implements PlanningDirectory {

    private final StudyPlanRepository plans;
    private final PlannedSessionRepository plannedSessions;
    private final AvailabilityWindowRepository windows;
    private final StudyGoalRepository goals;
    private final Limit historyLimit;

    /**
     * @param plans           plans
     * @param plannedSessions planned sessions
     * @param windows         availability windows
     * @param goals           goals
     * @param properties      configured limits of this module
     */
    public PlanningDirectoryService(StudyPlanRepository plans,
            PlannedSessionRepository plannedSessions, AvailabilityWindowRepository windows,
            StudyGoalRepository goals, PlanningProperties properties) {
        this.plans = plans;
        this.plannedSessions = plannedSessions;
        this.windows = windows;
        this.goals = goals;
        this.historyLimit = Limit.of(properties.maxPlanHistory());
    }

    @Override
    public Optional<StudyPlanView> activePlanOf(UUID accountId) {
        return plans.findByAccountIdAndStatus(accountId, PlanStatus.ACTIVE)
                .map(PlanningViews::of);
    }

    @Override
    public List<StudyPlanView> planHistoryOf(UUID accountId) {
        return plans.findByAccountIdOrderByCreatedAtDesc(accountId, historyLimit).stream()
                .map(PlanningViews::of)
                .toList();
    }

    @Override
    public Optional<StudyPlanView> plan(UUID planId) {
        return plans.findById(planId).map(PlanningViews::of);
    }

    @Override
    public List<PlannedSessionView> plannedSessionsOf(UUID accountId, Instant from, Instant to) {
        return plannedSessions.findInWindow(accountId, PlanStatus.ACTIVE, from, to).stream()
                .map(PlanningViews::of)
                .toList();
    }

    @Override
    public List<PlannedSessionView> sessionsOfPlan(UUID planId) {
        return plannedSessions.findByPlan(planId).stream()
                .map(PlanningViews::of)
                .toList();
    }

    @Override
    public Map<UUID, PlannedSessionView> plannedSessionsByIds(Collection<UUID> plannedSessionIds) {
        if (plannedSessionIds.isEmpty()) {
            return Map.of();
        }
        return plannedSessions.findAllById(plannedSessionIds).stream()
                .collect(Collectors.toMap(PlannedSession::id, PlanningViews::of,
                        (first, second) -> first, LinkedHashMap::new));
    }

    @Override
    public List<AvailabilityWindowView> availabilityOn(UUID accountId, LocalDate date) {
        return windows.findEffectiveOn(accountId, date).stream()
                .map(PlanningViews::of)
                .toList();
    }

    @Override
    public List<AvailabilityWindowView> availabilityOf(UUID accountId) {
        return windows.findByAccountIdOrderByDayOfWeekAscStartTimeAsc(accountId).stream()
                .map(PlanningViews::of)
                .toList();
    }

    @Override
    public List<StudyGoalView> activeGoalsOf(UUID accountId) {
        return goals.findByAccountIdAndStatusOrderByPriorityDescCreatedAtDesc(accountId,
                        GoalStatus.ACTIVE).stream()
                .map(PlanningViews::of)
                .toList();
    }

    @Override
    public List<StudyGoalView> goalsOf(UUID accountId) {
        return goals.findByAccountIdOrderByPriorityDescCreatedAtDesc(accountId).stream()
                .map(PlanningViews::of)
                .toList();
    }
}
