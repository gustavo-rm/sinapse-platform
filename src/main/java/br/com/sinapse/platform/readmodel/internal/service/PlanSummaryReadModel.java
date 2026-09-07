package br.com.sinapse.platform.readmodel.internal.service;

import br.com.sinapse.platform.curriculum.api.TopicView;
import br.com.sinapse.platform.learningrecord.api.AdherenceReport;
import br.com.sinapse.platform.learningrecord.api.StudyHistory;
import br.com.sinapse.platform.planning.api.PlannedSessionView;
import br.com.sinapse.platform.planning.api.PlanningDirectory;
import br.com.sinapse.platform.planning.api.StudyPlanView;
import br.com.sinapse.platform.readmodel.api.PlanSummaryView;
import br.com.sinapse.platform.readmodel.internal.error.NotReadableException;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code PlanoResumido}: a plan seen whole, with the share of it that actually happened.
 *
 * <p>Section 3.3 of the API contract.
 *
 * <p><strong>Which sessions count as missed is decided here, and that is deliberate.</strong>
 * The learning record does not know what a plan is (rule R2), so it cannot know which sessions
 * were scheduled or which of them have fallen due; it answers how many of a given set carry a
 * completed execution. Deciding the set is a planning question, and it is answered where the
 * schedule is known — which is what keeps "a session still in the future is not a missed one"
 * a rule rather than a date comparison guessed at in the wrong module.
 *
 * <p>Due means the scheduled <em>end</em> has passed. A session under way is not yet late, and
 * counting it would make a plan read worse at exactly the moment a student is most likely to
 * open it.
 */
@Service
public class PlanSummaryReadModel {

    private final PlanningDirectory planning;
    private final StudyHistory history;
    private final CatalogNames names;
    private final ReadModelAccess access;
    private final Clock clock;

    /**
     * @param planning the plan and its sessions
     * @param history  how many of the due ones were completed
     * @param names    topic and subject names, in two queries
     * @param access   the single gate these reads consult
     * @param clock    application clock, read to decide what has fallen due
     */
    public PlanSummaryReadModel(PlanningDirectory planning, StudyHistory history,
            CatalogNames names, ReadModelAccess access, Clock clock) {
        this.planning = planning;
        this.history = history;
        this.names = names;
        this.access = access;
        this.clock = clock;
    }

    /**
     * A summary of one of the caller's plans.
     *
     * @param accountId the caller
     * @param planId    plan to summarise
     * @return the summary
     * @throws NotReadableException if the plan does not exist or belongs to somebody else
     */
    @Transactional(readOnly = true)
    public PlanSummaryView of(UUID accountId, UUID planId) {
        access.requireOwnLearningData(accountId);

        StudyPlanView plan = planning.plan(planId)
                .filter(candidate -> candidate.accountId().equals(accountId))
                .orElseThrow(NotReadableException::new);

        List<PlannedSessionView> sessions = planning.sessionsOfPlan(planId);
        CatalogNames.Resolved resolved = names.of(sessions.stream()
                .map(PlannedSessionView::topicId)
                .collect(Collectors.toCollection(LinkedHashSet::new)));

        Instant now = clock.instant();
        List<UUID> due = sessions.stream()
                .filter(session -> !session.scheduledEnd().isAfter(now))
                .map(PlannedSessionView::id)
                .toList();
        AdherenceReport adherence = history.adherenceOf(accountId, due);

        return new PlanSummaryView(
                plan.id(),
                plan.status(),
                plan.horizonStart(),
                plan.horizonEnd(),
                plan.createdAt(),
                plan.supersededByPlanId(),
                sessions.stream().mapToInt(PlannedSessionView::durationMinutes).sum(),
                bySubject(sessions, resolved),
                adherenceOf(adherence));
    }

    /**
     * The plan's minutes broken down by subject, heaviest first.
     *
     * <p>A session whose topic is no longer in the catalogue keeps its minutes and loses its
     * subject: dropping it would make the breakdown disagree with the total, and a curation
     * decision taken after the plan was generated should not silently remove time the student
     * was asked to spend.
     */
    private static List<PlanSummaryView.SubjectPlan> bySubject(List<PlannedSessionView> sessions,
            CatalogNames.Resolved resolved) {

        Map<UUID, int[]> totals = new LinkedHashMap<>();
        for (PlannedSessionView session : sessions) {
            UUID subjectId = Optional.ofNullable(resolved.topics().get(session.topicId()))
                    .map(TopicView::subjectId)
                    .orElse(null);
            int[] total = totals.computeIfAbsent(subjectId, subject -> new int[2]);
            total[0] += session.durationMinutes();
            total[1]++;
        }
        return totals.entrySet().stream()
                .map(entry -> new PlanSummaryView.SubjectPlan(entry.getKey(),
                        entry.getKey() == null ? null : resolved.subjectName(entry.getKey()),
                        entry.getValue()[0], entry.getValue()[1]))
                .sorted(Comparator.comparingInt(PlanSummaryView.SubjectPlan::plannedMinutes).reversed())
                .toList();
    }

    /**
     * Turns the report into the shape the contract publishes.
     *
     * <p>The ratio is null when nothing has fallen due, never zero: zero reads as a student who
     * followed none of their plan, and a plan that starts tomorrow deserves no such statement.
     */
    static PlanSummaryView.Adherence adherenceOf(AdherenceReport report) {
        return new PlanSummaryView.Adherence(report.planned(), report.executed(),
                report.ratio().isPresent() ? report.ratio().getAsDouble() : null);
    }
}
