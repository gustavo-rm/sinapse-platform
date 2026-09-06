package br.com.sinapse.platform.curriculum.internal.config;

import br.com.sinapse.platform.curriculum.api.EffortTier;
import br.com.sinapse.platform.curriculum.api.EffortTiers;
import java.time.Duration;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Publishes the configured band-to-duration mapping.
 */
@Configuration(proxyBeanMethods = false)
public class EffortTierConfiguration {

    /**
     * Reads the mapping once, at startup, and refuses to start if a band is missing.
     *
     * <p>Failing here rather than later is deliberate. A band with no duration would surface
     * as a topic the snapshot assembly cannot size, in the middle of generating somebody's
     * plan, which is both far from the cause and much harder to read.
     *
     * @param properties configured durations
     * @return the mapping, complete and immutable
     * @throws IllegalStateException if any band has no configured duration
     */
    @Bean
    public EffortTiers effortTiers(CurriculumProperties properties) {
        Map<EffortTier, Duration> configured = new EnumMap<>(EffortTier.class);
        configured.putAll(properties.effortTierDuration());

        List<EffortTier> missing = Arrays.stream(EffortTier.values())
                .filter(tier -> !configured.containsKey(tier))
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "No duration configured under sinapse.curriculum.effort-tier-duration for "
                            + missing);
        }

        Map<EffortTier, Duration> mapping = Map.copyOf(configured);
        return new EffortTiers() {

            @Override
            public Duration plannedDurationOf(EffortTier tier) {
                return mapping.get(tier);
            }

            @Override
            public Map<EffortTier, Duration> mapping() {
                return mapping;
            }
        };
    }
}
