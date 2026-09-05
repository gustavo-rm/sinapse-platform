package br.com.sinapse.platform.shared.config;

import jakarta.validation.constraints.NotNull;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Time configuration, bound from {@code sinapse.time}.
 *
 * @param zone zone in which the application performs its internal operations. It is
 *             <em>not</em> the zone of any user: local times belonging to an account are
 *             always interpreted in {@code Account.timeZone} (ADR 0009). This value only
 *             ensures that the application never falls back to the zone of the JVM or of
 *             the host it happens to be running on.
 */
@Validated
@ConfigurationProperties("sinapse.time")
public record TimeProperties(@NotNull @DefaultValue("UTC") ZoneId zone) {
}
