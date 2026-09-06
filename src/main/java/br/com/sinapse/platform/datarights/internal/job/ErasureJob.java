package br.com.sinapse.platform.datarights.internal.job;

import br.com.sinapse.platform.datarights.internal.service.ErasureRequestService;
import br.com.sinapse.platform.datarights.internal.service.ErasureService;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Carries out the erasures whose seven days have elapsed.
 *
 * <p>Each request is its own transaction, so one that fails does not take the others with it.
 * Inside that transaction it is all or nothing: partial erasure is worse than none.
 *
 * <p><strong>The failure path is why this is a loop and not a stream.</strong> When an attempt
 * throws, its transaction has already rolled back and taken every write with it — including any
 * record that it was attempted. Marking the request failed therefore happens in a transaction of
 * its own, which is what {@code markFailed} declares.
 *
 * <p>The schedule and its zone are configuration. The zone is passed explicitly because
 * {@code @Scheduled} otherwise resolves the cron expression against the default zone of the JVM,
 * which nothing in this application may depend on. Setting the expression to {@code -} disables
 * it, which is what the test profile does.
 */
@Component
public class ErasureJob {

    /** What is recorded when an attempt fails: a category, never a message. */
    static final String FAILURE_REASON = "ERASURE_FAILED";

    private static final Logger LOG = LoggerFactory.getLogger(ErasureJob.class);

    private final ErasureRequestService requests;
    private final ErasureService erasure;

    /**
     * @param requests the request lifecycle
     * @param erasure  the erasure itself
     */
    public ErasureJob(ErasureRequestService requests, ErasureService erasure) {
        this.requests = requests;
        this.erasure = erasure;
    }

    /** Fires the daily pass. */
    @Scheduled(cron = "${sinapse.data-rights.sweep-cron}", zone = "${sinapse.time.zone}")
    public void run() {
        runOnce();
    }

    /**
     * Carries out every request that is due.
     *
     * <p>Public so that a test can drive one pass at a chosen moment rather than waiting for a
     * clock.
     *
     * @return how many requests were carried out
     */
    public int runOnce() {
        List<UUID> due = requests.due();
        int completed = 0;
        for (UUID requestId : due) {
            if (carryOut(requestId)) {
                completed++;
            }
        }
        return completed;
    }

    private boolean carryOut(UUID requestId) {
        try {
            erasure.erase(requestId);
            return true;
        } catch (RuntimeException failure) {
            // Nothing was erased: the transaction rolled back whole. The request is marked for
            // a person to look at, in a transaction of its own, and is not retried.
            LOG.error("Erasure request {} failed and was rolled back whole", requestId, failure);
            requests.markFailed(requestId, FAILURE_REASON);
            return false;
        }
    }
}
