package br.com.sinapse.platform.learningrecord.internal.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration of the learning record module, bound from {@code sinapse.learning-record}.
 *
 * @param maxHistoryWindow          the widest window a history may be asked for. Section 1 of
 *                                  the API contract requires the cap: a history grows without
 *                                  limit and there is no generic pagination to fall back on,
 *                                  so an uncapped window is a way of asking the server for
 *                                  everything
 * @param maxRetroactiveBackdating  how far back a session may be recorded after the fact.
 *                                  Retroactive entry is the marked exception of decision F5,
 *                                  and without a bound it becomes a way of writing a study
 *                                  history that was never studied
 * @param consistencyCheckCron      when the job that looks for references to planned sessions
 *                                  that no longer exist runs. Set to {@code -} to disable
 */
@Validated
@ConfigurationProperties("sinapse.learning-record")
public record LearningRecordProperties(

        @NotNull @DefaultValue("90d") Duration maxHistoryWindow,

        @NotNull @DefaultValue("7d") Duration maxRetroactiveBackdating,

        @DefaultValue("0 40 3 * * *") String consistencyCheckCron) {
}
