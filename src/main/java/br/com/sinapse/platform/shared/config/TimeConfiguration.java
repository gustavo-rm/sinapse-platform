package br.com.sinapse.platform.shared.config;

import com.fasterxml.jackson.databind.SerializationFeature;
import java.time.Clock;
import java.time.Duration;
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

    /** Resolution of {@code timestamptz}, which is what every instant is eventually stored at. */
    private static final Duration STORAGE_RESOLUTION = Duration.ofNanos(1_000);

    /**
     * The clock every component reads the current instant from.
     *
     * <p>It ticks in microseconds rather than nanoseconds, which is not a detail. PostgreSQL
     * stores {@code timestamptz} to the microsecond, so an instant read from the JVM at
     * nanosecond resolution is not the instant that comes back out of the database. Without
     * the truncation, the {@code createdAt} in the response to a write and the
     * {@code createdAt} in the next read of the same row differ in their last digits — the
     * same value, rendered two ways, which is exactly the kind of thing a client ends up
     * writing a comparison around.
     *
     * @param properties time configuration
     * @return a clock fixed to the configured application zone, at the resolution the
     *         database keeps
     */
    @Bean
    public Clock clock(TimeProperties properties) {
        return Clock.tick(Clock.system(properties.zone()), STORAGE_RESOLUTION);
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
