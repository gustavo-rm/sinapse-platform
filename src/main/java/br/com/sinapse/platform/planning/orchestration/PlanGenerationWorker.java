package br.com.sinapse.platform.planning.orchestration;

import br.com.sinapse.platform.planning.internal.service.GenerationRequestService;
import br.com.sinapse.platform.planning.internal.service.GenerationRequestService.ClaimedJob;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Takes work off the queue and runs it.
 *
 * <p>The queue is a table and the claim is {@code select ... for update skip locked}, so any
 * number of these may run at once: each takes rows the others are not holding, and two workers
 * never claim the same job. A broker would be infrastructure to operate, monitor and debug
 * without a problem at the pilot's scale that justifies it (ADR 0007).
 *
 * <p>The schedule and its zone are configuration. The zone is passed explicitly because
 * {@code @Scheduled} otherwise resolves the cron expression against the default zone of the
 * JVM, which nothing in this application may depend on. Setting the expression to {@code -}
 * disables it, which is what the test profile does: a worker that fires on its own in the
 * middle of a suite makes a failure depend on the second the suite ran.
 *
 * <p>Each pass first gives back to the queue any job whose worker died mid-attempt
 * ({@link OrphanedJobRecovery}), so that a job recovered in this pass can be claimed in the same
 * pass. Any instance can do it for any other: the recovery is one conditional statement.
 *
 * <p>{@link #runOnce()} holds no logic of its own and is public so that a test can drive one
 * pass at a chosen moment rather than waiting for a clock.
 */
@Component
public class PlanGenerationWorker {

    private final GenerationRequestService requests;
    private final PlanGenerationOrchestrator orchestrator;
    private final OrphanedJobRecovery orphans;

    /**
     * @param requests     the queue
     * @param orchestrator what a claimed job is put through
     * @param orphans      what gives back the jobs of a worker that died
     */
    public PlanGenerationWorker(GenerationRequestService requests,
            PlanGenerationOrchestrator orchestrator, OrphanedJobRecovery orphans) {
        this.requests = requests;
        this.orchestrator = orchestrator;
        this.orphans = orphans;
    }

    /** Fires one pass. */
    @Scheduled(cron = "${sinapse.planning.generation.poll-cron}", zone = "${sinapse.time.zone}")
    public void poll() {
        runOnce();
    }

    /**
     * Recovers orphaned jobs, then claims whatever is due and runs it.
     *
     * <p>The claim is its own short transaction and commits before anything is run: the rows it
     * takes are locked until it does, and the run that follows takes minutes.
     *
     * @return how many jobs this pass took
     */
    public int runOnce() {
        orphans.recover();
        List<ClaimedJob> claimed = requests.claimNext();
        claimed.forEach(orchestrator::run);
        return claimed.size();
    }
}
