package br.com.sinapse.platform.curriculum.internal.config;

import br.com.sinapse.platform.curriculum.api.EffortTier;
import jakarta.validation.constraints.NotEmpty;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration of the curriculum module, bound from {@code sinapse.curriculum}.
 *
 * @param effortTierDuration how long a topic of each band is planned for. It is configuration
 *                           and not code because ADR 0012 puts the number where it can be
 *                           calibrated against observed data rather than where it is
 *                           asserted. Every band has to have a value: a missing one would
 *                           mean a topic nobody can schedule, and failing at startup is
 *                           better than discovering that when a plan is generated.
 */
@Validated
@ConfigurationProperties("sinapse.curriculum")
public record CurriculumProperties(

        @NotEmpty @DefaultValue Map<EffortTier, Duration> effortTierDuration) {
}
