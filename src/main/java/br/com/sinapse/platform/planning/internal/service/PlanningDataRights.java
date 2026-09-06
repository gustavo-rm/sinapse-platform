package br.com.sinapse.platform.planning.internal.service;

import br.com.sinapse.platform.planning.api.GenerationRequestView;
import br.com.sinapse.platform.planning.api.PlanGenerationFailure;
import br.com.sinapse.platform.planning.api.PlanningDirectory;
import br.com.sinapse.platform.planning.api.StudyPlanView;
import br.com.sinapse.platform.planning.internal.domain.PlanGenerationRequest;
import br.com.sinapse.platform.planning.internal.persistence.AvailabilityWindowRepository;
import br.com.sinapse.platform.planning.internal.persistence.PlanGenerationRequestRepository;
import br.com.sinapse.platform.planning.internal.persistence.PlannedSessionRepository;
import br.com.sinapse.platform.planning.internal.persistence.StudyGoalRepository;
import br.com.sinapse.platform.planning.internal.persistence.StudyPlanRepository;
import br.com.sinapse.platform.shared.datarights.ModuleDataRights;
import br.com.sinapse.platform.shared.datarights.ModuleExport;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * What this module owes the holder of an account.
 *
 * <p>Everything here is erased and nothing survives: availability, goals, plans, the sessions
 * inside them and the generation jobs that produced them. ADR 0011 lists all five, and the
 * generation job is singled out — its {@code snapshot} holds the student's whole state in one
 * document and is the most sensitive artifact in the system.
 *
 * <p><strong>The order inside this method is a foreign key order, not a preference.</strong>
 * Planned sessions reference their plan, and a plan references the job that produced it, so
 * they go in that sequence. Doing it the other way round would fail on a constraint rather than
 * leave anything half-erased, but failing at all in the middle of an Article 18 request is not
 * a thing to leave to chance.
 *
 * <p>The export leaves out the snapshot document. Every value in it is a copy of data this same
 * export already carries in its own right — the availability, the goals, the topics and the
 * history it was assembled from — so including it would duplicate the most sensitive artifact
 * in the system into a downloadable file without telling the holder anything new.
 */
@Component
@Order(PlanningDataRights.ORDER)
public class PlanningDataRights implements ModuleDataRights {

    /** After the learning record: nothing there references a plan, and nothing here does. */
    public static final int ORDER = 20;

    private static final Logger LOG = LoggerFactory.getLogger(PlanningDataRights.class);

    private final PlannedSessionRepository plannedSessions;
    private final StudyPlanRepository plans;
    private final PlanGenerationRequestRepository requests;
    private final StudyGoalRepository goals;
    private final AvailabilityWindowRepository windows;
    private final PlanningDirectory directory;

    /**
     * @param plannedSessions planned sessions
     * @param plans           plans
     * @param requests        generation jobs
     * @param goals           goals
     * @param windows         availability windows
     * @param directory       reads of this module, for the export
     */
    public PlanningDataRights(PlannedSessionRepository plannedSessions, StudyPlanRepository plans,
            PlanGenerationRequestRepository requests, StudyGoalRepository goals,
            AvailabilityWindowRepository windows, PlanningDirectory directory) {
        this.plannedSessions = plannedSessions;
        this.plans = plans;
        this.requests = requests;
        this.goals = goals;
        this.windows = windows;
        this.directory = directory;
    }

    @Override
    public String moduleName() {
        return "planning";
    }

    @Override
    @Transactional
    public void eraseFor(UUID accountId) {
        int sessions = plannedSessions.eraseFor(accountId);
        int erasedPlans = plans.eraseFor(accountId);
        int jobs = requests.eraseFor(accountId);
        int erasedGoals = goals.eraseFor(accountId);
        int erasedWindows = windows.eraseFor(accountId);

        // Counts and nothing else. What was planned, and when, is the data being removed.
        LOG.info("Erasure removed {} planned sessions, {} plans, {} generation jobs including "
                        + "their snapshots, {} goals and {} availability windows",
                sessions, erasedPlans, jobs, erasedGoals, erasedWindows);
    }

    @Override
    @Transactional(readOnly = true)
    public ModuleExport exportFor(UUID accountId) {
        Map<String, List<Object>> collections = new LinkedHashMap<>();
        collections.put("availability", List.copyOf(directory.availabilityOf(accountId)));
        collections.put("goals", List.copyOf(directory.goalsOf(accountId)));

        List<StudyPlanView> history = directory.planHistoryOf(accountId);
        collections.put("studyPlans", List.copyOf(history));
        collections.put("plannedSessions", history.stream()
                .flatMap(plan -> directory.sessionsOfPlan(plan.id()).stream())
                .map(Object.class::cast)
                .toList());
        collections.put("generationRequests", requests.findByAccountIdOrderByRequestedAtDesc(
                        accountId).stream()
                .map(PlanningDataRights::jobOf)
                .toList());
        return new ModuleExport(moduleName(), collections);
    }

    /**
     * A job as the holder sees it, without the snapshot.
     *
     * <p>The plan it produced is not resolved here: the export already carries every plan of
     * the account, and each of those names the job it came from.
     */
    private static Object jobOf(PlanGenerationRequest request) {
        return new GenerationRequestView(request.id(), request.status(), request.horizonStart(),
                request.horizonEnd(), request.requestedAt(), request.startedAt(),
                request.finishedAt(), request.attemptCount(),
                request.failureReason() == null
                        ? null
                        : PlanGenerationFailure.valueOf(request.failureReason()),
                null, null);
    }
}
