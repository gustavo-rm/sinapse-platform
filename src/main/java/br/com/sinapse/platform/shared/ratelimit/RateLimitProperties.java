package br.com.sinapse.platform.shared.ratelimit;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Rate limiting configuration, bound from {@code sinapse.rate-limit}.
 *
 * @param trustedProxies CIDR blocks of the proxies whose {@code X-Forwarded-For} may be
 *                       believed. Empty by default: with no entry, the header is ignored
 *                       and the address seen by the container is used. Configure it only
 *                       for the addresses of the reverse proxy actually in front of the
 *                       application.
 * @param routes         policies, evaluated in declaration order; the first match wins
 */
@Validated
@ConfigurationProperties("sinapse.rate-limit")
public record RateLimitProperties(

        @DefaultValue List<String> trustedProxies,

        @Valid @DefaultValue List<Route> routes) {

    /**
     * Policy of a single route.
     *
     * @param id     stable name of the policy, used to separate counters
     * @param path   path pattern, matched against the request path
     * @param phase  point of the chain at which the policy applies
     * @param limit  requests accepted within the window
     * @param window length of the window
     */
    public record Route(
            @NotBlank String id,
            @NotBlank String path,
            @NotNull RateLimitPhase phase,
            @Positive int limit,
            @NotNull Duration window) {
    }
}
