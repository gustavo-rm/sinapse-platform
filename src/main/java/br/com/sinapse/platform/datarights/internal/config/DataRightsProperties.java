package br.com.sinapse.platform.datarights.internal.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration of the data subject rights coordinator, bound from {@code sinapse.data-rights}.
 *
 * @param erasureWindow how long a holder has to withdraw an erasure request. Seven days by ADR
 *                      0011, and configurable rather than constant because the adequacy of that
 *                      period is one of the three points the ADR leaves for legal confirmation
 * @param sweepCron     when the job that carries out due erasures runs. Set to {@code -} to
 *                      disable
 * @param batchSize     how many due requests one pass carries out, so that a pass is bounded
 *                      whatever has accumulated
 */
@Validated
@ConfigurationProperties("sinapse.data-rights")
public record DataRightsProperties(

        @NotNull @DefaultValue("7d") Duration erasureWindow,

        @NotBlank @DefaultValue("0 20 3 * * *") String sweepCron,

        @Positive @DefaultValue("50") int batchSize) {
}
