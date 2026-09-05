package br.com.sinapse.platform.shared.config;

import br.com.sinapse.platform.shared.ratelimit.ClientAddressResolver;
import br.com.sinapse.platform.shared.ratelimit.InMemoryRateLimiter;
import br.com.sinapse.platform.shared.ratelimit.RateLimitFilter;
import br.com.sinapse.platform.shared.ratelimit.RateLimitPhase;
import br.com.sinapse.platform.shared.ratelimit.RateLimitPolicies;
import br.com.sinapse.platform.shared.web.problem.ApiErrorType;
import br.com.sinapse.platform.shared.web.problem.ProblemDetailWriter;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.header.HeaderWriterFilter;

/**
 * Security filter chains and the deliberate position of rate limiting within them.
 *
 * <p>No authentication mechanism exists yet: sessions are opaque server-side tokens and
 * arrive with the identity module (ADR 0010). Until then every route is open, which is
 * accurate rather than permissive, because no protected resource exists.
 *
 * <p><strong>Filter order.</strong> ADR 0009 requires rate limiting to run before
 * authentication for anonymous routes and after it for authenticated ones. The two
 * registrations below are anchored on framework filters that are always present, so the
 * order does not shift when an authentication filter is added later:
 *
 * <ul>
 *   <li>the {@code BEFORE_AUTHENTICATION} instance is placed right after
 *       {@link HeaderWriterFilter}, ahead of every filter that establishes an identity,
 *       so that an anonymous flood is rejected before any authentication work;</li>
 *   <li>the {@code AFTER_AUTHENTICATION} instance is placed immediately before
 *       {@link AuthorizationFilter}, the last filter of the chain, so that the security
 *       context is already populated and the counter can be keyed by account.</li>
 * </ul>
 *
 * <p>{@code FilterOrderIntegrationTest} asserts these positions against the chain that is
 * actually built.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class SecurityConfiguration {

    /**
     * Chain of the management endpoints.
     *
     * <p>Actuator listens on its own port, bound to the loopback address, and is not
     * published by the infrastructure. Access control on that port is the deployment's
     * responsibility; adding credentials here would only give the impression of one.
     *
     * @param http builder for this chain
     * @return the management chain
     * @throws Exception if the chain cannot be built
     */
    @Bean
    @Order(1)
    public SecurityFilterChain managementFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher(EndpointRequest.toAnyEndpoint())
                .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .build();
    }

    /**
     * Chain of the public API.
     *
     * @param http                  builder for this chain
     * @param policies              configured rate limit policies
     * @param limiter               counter store
     * @param clientAddressResolver resolver of the address to attribute a request to
     * @param problemDetailWriter   writer of error bodies outside the dispatcher
     * @return the API chain
     * @throws Exception if the chain cannot be built
     */
    @Bean
    @Order(2)
    public SecurityFilterChain apiFilterChain(HttpSecurity http, RateLimitPolicies policies,
            InMemoryRateLimiter limiter, ClientAddressResolver clientAddressResolver,
            ProblemDetailWriter problemDetailWriter) throws Exception {

        RateLimitFilter beforeAuthentication = new RateLimitFilter(RateLimitPhase.BEFORE_AUTHENTICATION,
                policies, limiter, clientAddressResolver, problemDetailWriter);
        RateLimitFilter afterAuthentication = new RateLimitFilter(RateLimitPhase.AFTER_AUTHENTICATION,
                policies, limiter, clientAddressResolver, problemDetailWriter);

        return http
                // The API is consumed with an opaque token in a header, never with an
                // ambient cookie, so there is no cross-site request forgery vector to
                // protect against. Revisit if a cookie is ever introduced.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint(problemDetailWriter))
                        .accessDeniedHandler(accessDeniedHandler(problemDetailWriter)))
                .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                .addFilterAfter(beforeAuthentication, HeaderWriterFilter.class)
                .addFilterBefore(afterAuthentication, AuthorizationFilter.class)
                .build();
    }

    /**
     * Answers a missing or invalid credential with the shared error contract instead of
     * the container's default page.
     */
    private static AuthenticationEntryPoint authenticationEntryPoint(ProblemDetailWriter writer) {
        return (request, response, exception) ->
                writer.write(request, response, ApiErrorType.UNAUTHENTICATED);
    }

    /**
     * Answers a denied authorisation with the shared error contract.
     */
    private static AccessDeniedHandler accessDeniedHandler(ProblemDetailWriter writer) {
        return (request, response, exception) ->
                writer.write(request, response, ApiErrorType.ACCESS_DENIED);
    }
}
