package br.com.sinapse.platform.identity.internal.job;

import br.com.sinapse.platform.identity.internal.service.MajorityTransitionService;
import java.time.Clock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the majority sweep once a day.
 *
 * <p>The schedule and its zone are configuration. The zone is passed explicitly because
 * {@code @Scheduled} otherwise resolves the cron expression against the default zone of the
 * JVM, which ADR 0009 forbids anything in this application from depending on.
 *
 * <p>Setting {@code sinapse.identity.majority-sweep-cron} to {@code -} disables the
 * schedule, which is what the test profile does: a job that fires on its own in the middle
 * of a suite makes failures depend on the hour the suite ran.
 *
 * <p>The job holds no logic. It reads the clock and calls the service, which takes the
 * instant as an argument so that the same rule can be exercised at a chosen date.
 */
@Component
public class MajorityTransitionJob {

    private final MajorityTransitionService sweep;
    private final Clock clock;

    /**
     * @param sweep the rule
     * @param clock application clock
     */
    public MajorityTransitionJob(MajorityTransitionService sweep, Clock clock) {
        this.sweep = sweep;
        this.clock = clock;
    }

    /** Fires the daily pass. */
    @Scheduled(cron = "${sinapse.identity.majority-sweep-cron}", zone = "${sinapse.time.zone}")
    public void run() {
        sweep.sweep(clock.instant());
    }
}
