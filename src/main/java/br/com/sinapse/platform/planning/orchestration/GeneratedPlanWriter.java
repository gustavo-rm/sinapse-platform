package br.com.sinapse.platform.planning.orchestration;

import br.com.sinapse.platform.coreclient.contract.PlanResponse;
import br.com.sinapse.platform.planning.api.PlannedSessionKind;
import br.com.sinapse.platform.planning.api.StudyPlanView;
import br.com.sinapse.platform.planning.internal.service.GenerationRequestService;
import br.com.sinapse.platform.planning.internal.service.GenerationRequestService.ClaimedJob;
import br.com.sinapse.platform.planning.internal.service.StudyPlanService;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes what the core produced, in one transaction.
 *
 * <p><strong>One transaction is the requirement, not a convenience.</strong> Storing a plan
 * supersedes the one it replaces and inserts every session it contains, and marking the job
 * finished is part of the same fact. A failure part-way through has to leave the student with
 * the plan they had — a half-written plan would be immutable by trigger and would sit there
 * forever, and a plan with no finished job would be a plan nobody asked for.
 *
 * <p>It is a component of its own so that the transaction is real. The orchestrator that calls
 * it spends minutes waiting on HTTP and must not be inside a transaction; a method annotated on
 * the same bean and called from within it would be neither.
 */
@Component
public class GeneratedPlanWriter {

    private final StudyPlanService plans;
    private final GenerationRequestService requests;

    /**
     * @param plans    plan storage and supersession
     * @param requests the job's state machine
     */
    public GeneratedPlanWriter(StudyPlanService plans, GenerationRequestService requests) {
        this.plans = plans;
        this.requests = requests;
    }

    /**
     * Stores the plan and closes the job.
     *
     * @param job      the job that produced it
     * @param response what the core answered
     * @return the stored plan
     */
    @Transactional
    public StudyPlanView store(ClaimedJob job, PlanResponse response) {
        List<StudyPlanService.ScheduledSession> sessions = response.sessions().stream()
                .map(GeneratedPlanWriter::sessionOf)
                .toList();

        StudyPlanView plan = plans.store(job.accountId(), job.id(), job.horizonStart(),
                job.horizonEnd(), response.fitness(), sessions);
        requests.succeed(job.id(), response.metadata().coreVersion());
        return plan;
    }

    private static StudyPlanService.ScheduledSession sessionOf(PlanResponse.ScheduledSession session) {
        return new StudyPlanService.ScheduledSession(session.topicId(),
                PlannedSessionKind.valueOf(session.kind().name()), session.scheduledStart(),
                session.durationMinutes(), session.sequenceIndex());
    }
}
