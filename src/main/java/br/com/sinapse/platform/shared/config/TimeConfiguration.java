package br.com.sinapse.platform.shared.config;

import com.fasterxml.jackson.databind.SerializationFeature;
import java.time.Clock;
import java.util.TimeZone;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Makes the application's notion of "now" explicit.
 *
 * <p>ADR 0009 forbids any dependency on the default zone of the JVM or of the server.
 * The consequence is that no production code may call {@code Instant.now()},
 * {@code LocalDate.now()} or {@code ZoneId.systemDefault()}: it asks this {@link Clock}
 * instead. {@code DefaultTimeZoneIndependenceTest} fails the build when that is
 * violated.
 */
@Configuration(proxyBeanMethods = false)
public class TimeConfiguration {

    /**
     * The clock every component reads the current instant from.
     *
     * @param properties time configuration
     * @return a clock fixed to the configured application zone
     */
    @Bean
    public Clock clock(TimeProperties properties) {
        return Clock.system(properties.zone());
    }

    /**
     * Serialises instants as ISO-8601 with an offset, as required by the API contract,
     * instead of as epoch numbers, and pins the mapper's zone to the application zone so
     * that the rendering does not change with the host.
     *
     * @param properties time configuration
     * @return the customiser applied to the application's object mapper
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer isoInstantsCustomizer(TimeProperties properties) {
        return builder -> builder
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .timeZone(TimeZone.getTimeZone(properties.zone()));
    }
}
