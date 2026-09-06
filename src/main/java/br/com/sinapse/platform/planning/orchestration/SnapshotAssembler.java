package br.com.sinapse.platform.planning.orchestration;

import br.com.sinapse.platform.coreclient.contract.EdgeProvenance;
import br.com.sinapse.platform.coreclient.contract.EdgeStrength;
import br.com.sinapse.platform.coreclient.contract.PlanRequest;
import br.com.sinapse.platform.coreclient.contract.RecallRating;
import br.com.sinapse.platform.curriculum.api.CurriculumCatalog;
import br.com.sinapse.platform.curriculum.api.EffortTiers;
import br.com.sinapse.platform.curriculum.api.PrerequisiteEdgeView;
import br.com.sinapse.platform.curriculum.api.PrerequisiteGraph;
import br.com.sinapse.platform.curriculum.api.TopicView;
import br.com.sinapse.platform.identity.api.AccountDirectory;
import br.com.sinapse.platform.learningrecord.api.StudyHistory;
import br.com.sinapse.platform.learningrecord.api.StudySessionView;
import br.com.sinapse.platform.planning.api.AvailabilityWindowView;
import br.com.sinapse.platform.planning.api.PlanningDirectory;
import br.com.sinapse.platform.planning.api.StudyGoalView;
import br.com.sinapse.platform.planning.internal.config.PlanningProperties;
import br.com.sinapse.platform.planning.internal.service.GenerationRequestService.ClaimedJob;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the one document the optimiser is given.
 *
 * <p><strong>This is the component that knows more than one context, and the only one.</strong>
 * It reads the curriculum for topics and edges, the learning record for what has been studied,
 * planning for availability and goals, and identity for the zone those local times are read in.
 * Section 9.5 puts it here precisely so that {@code planning} never reads {@code learningrecord}
 * itself, which is what keeps the dependency graph acyclic (rule R2).
 *
 * <p>Everything it produces is read at assembly time and nothing is stored anywhere but in the
 * snapshot. That includes the per-student effort adjustment, which is derived from the same
 * history the snapshot carries.
 */
@Component
public class SnapshotAssembler {

    private final PlanningDirectory planning;
    private final CurriculumCatalog catalog;
    private final PrerequisiteGraph graph;
    private final EffortTiers effortTiers;
    private final StudyHistory history;
    private final AccountDirectory accounts;
    private final PlanningProperties.Generation configuration;
    private final Clock clock;

    /**
     * @param planning       availability and goals
     * @param catalog        topics of the subjects being pursued
     * @param graph          the prerequisite edges among them
     * @param effortTiers    the configured band-to-minutes mapping
     * @param history        what the student has already studied
     * @param accounts       the zone the student's local times are read in
     * @param properties     configured limits of this module, including what to ask the
     *                       optimiser for
     * @param clock          application clock
     */
    public SnapshotAssembler(PlanningDirectory planning, CurriculumCatalog catalog,
            PrerequisiteGraph graph, EffortTiers effortTiers, StudyHistory history,
            AccountDirectory accounts, PlanningProperties properties, Clock clock) {
        this.planning = planning;
        this.catalog = catalog;
        this.graph = graph;
        this.effortTiers = effortTiers;
        this.history = history;
        this.accounts = accounts;
        this.configuration = properties.generation();
        this.clock = clock;
    }

    /**
     * Assembles the snapshot for one job.
     *
     * @param job        the claimed job
     * @param randomSeed the seed this run will use, chosen by the backend
     * @return the document to send, which is also the document to store
     * @throws NothingToPlanException if the student has no goals or no availability left over
     *                                the horizon
     */
    @Transactional(readOnly = true)
    public PlanRequest assemble(ClaimedJob job, long randomSeed) {
        List<StudyGoalView> goals = planning.activeGoalsOf(job.accountId());
        if (goals.isEmpty()) {
            throw new NothingToPlanException("the student has no active goal");
        }

        List<PlanRequest.AvailabilitySlot> slots = availabilityOver(job);
        if (slots.isEmpty()) {
            throw new NothingToPlanException("the student has no availability in the horizon");
        }

        Instant now = clock.instant();
        List<StudySessionView> sessions = history.sessionsOf(job.accountId(),
                now.minus(configuration.historyWindow()), now);
        double effortFactor = EffortCalibration.factorOf(sessions,
                configuration.minCalibrationSessions(), configuration.minEffortFactor(),
                configuration.maxEffortFactor());

        Set<UUID> subjectIds = goals.stream()
                .map(StudyGoalView::subjectId)
                .collect(Collectors.toSet());

        return new PlanRequest(
                PlanRequest.VERSION,
                new PlanRequest.Horizon(job.horizonStart(), job.horizonEnd()),
                slots,
                goals.stream().map(SnapshotAssembler::goalOf).toList(),
                catalog.topicsOfSubjects(subjectIds).stream()
                        .map(topic -> topicOf(topic, effortFactor))
                        .toList(),
                graph.edgesTouchingSubjects(subjectIds).stream()
                        .map(SnapshotAssembler::edgeOf)
                        .toList(),
                historyOf(sessions),
                configuration.algorithmParams(),
                randomSeed);
    }

    /**
     * Turns the student's weekly routine into the actual intervals of the horizon.
     *
     * <p>Resolved in the student's own zone, which is why identity is read at all. A local time
     * that falls in a daylight-saving gap is moved forward by the standard rules rather than
     * dropped, and an interval that comes out empty after that resolution is left out — the
     * plan should not be told about an hour that does not exist.
     */
    private List<PlanRequest.AvailabilitySlot> availabilityOver(ClaimedJob job) {
        ZoneId zone = accounts.timeZoneOf(job.accountId())
                .orElseThrow(() -> new NothingToPlanException("the account has no time zone"));

        List<AvailabilityWindowView> windows = planning.availabilityOf(job.accountId()).stream()
                .filter(window -> window.isEffectiveDuring(job.horizonStart(), job.horizonEnd()))
                .toList();

        List<PlanRequest.AvailabilitySlot> slots = new ArrayList<>();
        for (LocalDate day = job.horizonStart(); !day.isAfter(job.horizonEnd());
                day = day.plusDays(1)) {
            for (AvailabilityWindowView window : windows) {
                if (window.dayOfWeek() != day.getDayOfWeek() || !window.isEffectiveOn(day)) {
                    continue;
                }
                Instant start = day.atTime(window.startTime()).atZone(zone).toInstant();
                Instant end = day.atTime(window.endTime()).atZone(zone).toInstant();
                if (end.isAfter(start)) {
                    slots.add(new PlanRequest.AvailabilitySlot(start, end));
                }
            }
        }
        slots.sort(Comparator.comparing(PlanRequest.AvailabilitySlot::start));
        return slots;
    }

    /**
     * Summarises what the student has done, per topic.
     *
     * <p>Built from one read of the session history rather than from three aggregate queries,
     * because the same list is what the effort adjustment is derived from. Sessions arrive
     * newest first and the ratings have to travel oldest first: the sequence is the signal.
     */
    private static List<PlanRequest.TopicHistory> historyOf(List<StudySessionView> sessions) {
        Map<UUID, TopicSummary> byTopic = new LinkedHashMap<>();
        for (StudySessionView session : sessions.reversed()) {
            byTopic.computeIfAbsent(session.topicId(), topic -> new TopicSummary()).add(session);
        }
        return byTopic.entrySet().stream()
                .map(entry -> entry.getValue().toContract(entry.getKey()))
                .toList();
    }

    private static PlanRequest.Goal goalOf(StudyGoalView goal) {
        return new PlanRequest.Goal(goal.subjectId(), goal.targetDate(), goal.priority());
    }

    private PlanRequest.Topic topicOf(TopicView topic, double effortFactor) {
        long bandMinutes = effortTiers.plannedDurationOf(topic.effortTier()).toMinutes();
        int estimated = Math.max(1, Math.toIntExact(Math.round(bandMinutes * effortFactor)));
        return new PlanRequest.Topic(topic.id(), topic.subjectId(), topic.position(),
                topic.effortTier().name(), estimated);
    }

    /**
     * Edges are sent as the graph reports them, including those with one endpoint outside the
     * subjects being planned. Dropping those would discard exactly the cross-subject
     * prerequisites the graph exists to record.
     */
    private static PlanRequest.PrerequisiteEdge edgeOf(PrerequisiteEdgeView edge) {
        return new PlanRequest.PrerequisiteEdge(edge.prerequisiteTopicId(),
                edge.dependentTopicId(),
                EdgeStrength.valueOf(edge.strength().name()),
                EdgeProvenance.valueOf(edge.provenance().name()));
    }

    /** What one topic's sessions add up to, accumulated in the order they happened. */
    private static final class TopicSummary {

        private int sessionCount;
        private long totalMinutes;
        private Instant lastStudiedAt;
        private final List<RecallRating> ratings = new ArrayList<>();

        private void add(StudySessionView session) {
            if (session.actualDurationMinutes() != null) {
                sessionCount++;
                totalMinutes += session.actualDurationMinutes();
                lastStudiedAt = session.endedAt();
            }
            if (session.recallRating() != null) {
                ratings.add(RecallRating.valueOf(session.recallRating().name()));
            }
        }

        private PlanRequest.TopicHistory toContract(UUID topicId) {
            return new PlanRequest.TopicHistory(topicId, sessionCount, totalMinutes, lastStudiedAt,
                    ratings);
        }
    }
}
