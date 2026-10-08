package br.com.sinapse.platform.planning.orchestration;

import br.com.sinapse.platform.coreclient.api.CoreCallLimits;
import br.com.sinapse.platform.planning.internal.config.PlanningProperties;
import br.com.sinapse.platform.planning.internal.service.GenerationRequestService;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Finds the jobs whose worker died and gives them back to the queue.
 *
 * <p>A worker that dies mid-call — the process killed, the machine restarted — leaves its job
 * {@code RUNNING}, and nothing else would ever move it: the claim takes only {@code PENDING}
 * rows, and the account's partial index refuses the student a new job while that one is
 * unfinished. This is the one place that notices.
 *
 * <p><strong>The bound is derived, never written down.</strong> An attempt cannot outlast the
 * call it makes to the core, and that call is bounded by the adapter's connect and read
 * timeouts; the configured margin covers the work around the call and clock skew between
 * instances. Read from the adapter's own configuration, so that raising the read timeout raises
 * this bound with it. A constant here would be a second copy of the timeout, and the day the two
 * disagree a legitimate ten-minute run is "recovered" in the middle.
 *
 * <p>The retry backoff and the attempt count are deliberately not part of it. The bound is on a
 * single attempt, measured from {@code started_at}, which every claim stamps afresh; between
 * attempts a job is {@code PENDING}, not {@code RUNNING}, so the backoff is never spent inside
 * the interval this measures.
 *
 * <p>Composes the core adapter's limits with this module's state machine, which is why it sits
 * in orchestration rather than in either of them.
 */
@Component
public class OrphanedJobRecovery {

    private static final Logger LOG = LoggerFactory.getLogger(OrphanedJobRecovery.class);

    private final GenerationRequestService requests;
    private final CoreCallLimits core;
    private final Duration margin;

    /**
     * @param requests   the job's state machine
     * @param core       how long a call to the core may last
     * @param properties configured margin over that
     */
    public OrphanedJobRecovery(GenerationRequestService requests, CoreCallLimits core,
            PlanningProperties properties) {
        this.requests = requests;
        this.core = core;
        this.margin = properties.generation().orphanMargin();
    }

    /**
     * How long a job may stay running before it is taken for orphaned.
     *
     * @return the longest call to the core plus the configured margin
     */
    public Duration longestAttempt() {
        return core.longestCall().plus(margin);
    }

    /**
     * Requeues or fails every job running for longer than {@link #longestAttempt()}.
     *
     * @return how many jobs were recovered
     */
    public int recover() {
        int recovered = requests.recoverOrphans(longestAttempt());
        if (recovered > 0) {
            LOG.warn("Recovered {} generation job(s) left running by a worker that is gone",
                    recovered);
        }
        return recovered;
    }
}
