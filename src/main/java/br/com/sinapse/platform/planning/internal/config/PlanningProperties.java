package br.com.sinapse.platform.planning.internal.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Duration;
import java.util.Map;
import java.time.Period;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration of the planning module, bound from {@code sinapse.planning}.
 *
 * @param maxScheduleWindow the widest window a schedule may be asked for. Section 1 of the API
 *                          contract requires the cap: planned sessions accumulate with every
 *                          plan and there is no generic pagination to fall back on
 * @param maxPlanHistory    ceiling on how many plans a history read returns. Re-planning is
 *                          manual, so this list grows in single figures; the ceiling is what
 *                          keeps "returned whole" from meaning "returned however large it got"
 * @param generation        how plan generation runs
 */
@Validated
@ConfigurationProperties("sinapse.planning")
public record PlanningProperties(

        @NotNull @DefaultValue("90d") Duration maxScheduleWindow,

        @Positive @DefaultValue("100") int maxPlanHistory,

        @Valid @DefaultValue Generation generation) {

    /**
     * How plan generation runs.
     *
     * @param horizon                  how far ahead a plan is generated. Four weeks by decision
     *                                 F3, and configurable rather than constant. Preparation
     *                                 can run for a year; generating a year of plan is
     *                                 expensive and mostly wrong, because availability and
     *                                 knowledge change long before that. A goal's target date
     *                                 is pressure inside the horizon, never the horizon itself
     * @param maxAttempts              how many times a job may be attempted before it is left
     *                                 failed
     * @param retryBackoff             base of the exponential backoff between attempts. The
     *                                 second attempt waits this long, the third twice as long
     * @param batchSize                how many jobs one pass of the worker claims
     * @param pollCron                 when the worker looks for work. Set to {@code -} to
     *                                 disable, which is what the test profile does: a worker
     *                                 that fires on its own in the middle of a suite makes a
     *                                 failure depend on the second the suite ran
     * @param historyWindow            how much of the student's past a run considers. It feeds
     *                                 the snapshot's per-topic history and the effort
     *                                 adjustment derived from it, which are the same question
     *                                 asked twice and so take the same window
     * @param minCalibrationSessions   how many completed sessions are needed before the
     *                                 adjustment is applied at all. Below it the configured
     *                                 band value is used unchanged, because a ratio computed
     *                                 from one session is not a measurement of anything
     * @param algorithmParams          parameters every run asks the optimiser to use. Stored
     *                                 on the job alongside the snapshot and the seed: without
     *                                 all four a plan generated today cannot be regenerated
     *                                 tomorrow. What they mean belongs to the core's own
     *                                 contract, which is why they are an opaque map here
     * @param minEffortFactor          floor on the adjustment
     * @param maxEffortFactor          ceiling on the adjustment. THE BOUNDS ARE A JUDGEMENT,
     *                                 NOT A FINDING. They exist because an unbounded ratio lets
     *                                 a handful of unusual sessions rewrite every estimate in
     *                                 the plan, and they should be revisited against observed
     *                                 data rather than kept because they were written here
     */
    public record Generation(

            @NotNull @DefaultValue("4w") Period horizon,

            @Positive @DefaultValue("3") int maxAttempts,

            @NotNull @DefaultValue("30s") Duration retryBackoff,

            @Positive @DefaultValue("1") int batchSize,

            @NotBlank @DefaultValue("0/10 * * * * *") String pollCron,

            @NotNull @DefaultValue("90d") Duration historyWindow,

            @DefaultValue Map<String, Object> algorithmParams,

            @PositiveOrZero @DefaultValue("5") int minCalibrationSessions,

            @Positive @DefaultValue("0.5") double minEffortFactor,

            @Positive @DefaultValue("2.0") double maxEffortFactor) {
    }
}
