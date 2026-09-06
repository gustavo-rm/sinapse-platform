package br.com.sinapse.platform.planning.orchestration;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the orphan reference check once a day.
 *
 * <p>The schedule and its zone are configuration. The zone is passed explicitly because
 * {@code @Scheduled} otherwise resolves the cron expression against the default zone of the
 * JVM, which nothing in this application may depend on.
 *
 * <p>Setting {@code sinapse.learning-record.consistency-check-cron} to {@code -} disables the
 * schedule, which is what the test profile does: a job that fires on its own in the middle of
 * a suite makes failures depend on the hour the suite ran.
 *
 * <p>The job holds no logic and swallows nothing. It calls the service, whose result is a
 * list a caller can act on, and whose logging is what an operator sees.
 */
@Component
public class PlannedSessionConsistencyJob {

    private final PlannedSessionConsistencyService check;

    /**
     * @param check the check
     */
    public PlannedSessionConsistencyJob(PlannedSessionConsistencyService check) {
        this.check = check;
    }

    /** Fires the daily pass. */
    @Scheduled(cron = "${sinapse.learning-record.consistency-check-cron}",
            zone = "${sinapse.time.zone}")
    public void run() {
        check.findOrphanReferences();
    }
}
