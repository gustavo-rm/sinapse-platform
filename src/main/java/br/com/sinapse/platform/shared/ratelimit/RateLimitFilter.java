package br.com.sinapse.platform.shared.ratelimit;

import br.com.sinapse.platform.shared.web.problem.ApiErrorType;
import br.com.sinapse.platform.shared.web.problem.ProblemDetailWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * The single rate limiting filter.
 *
 * <p>One instance is placed in the security chain before authentication and another
 * after it. Each only handles the policies declared for its own phase, so the position
 * of a policy in the chain is a property of the configuration, not of the order in
 * which filters happened to be registered. See {@code SecurityConfig} for the two
 * registrations and {@code FilterOrderIntegrationTest} for the assertion that the
 * order is what it claims to be.
 *
 * <p>The rejection body is the same RFC 7807 contract used everywhere else, written
 * through {@link ProblemDetailWriter} because a filter runs outside the dispatcher and
 * cannot reach {@code GlobalExceptionHandler}.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    /** Prefix of counters keyed by client address. */
    private static final String ADDRESS_KEY_PREFIX = "address:";

    /** Prefix of counters keyed by authenticated account. */
    private static final String ACCOUNT_KEY_PREFIX = "account:";

    private final RateLimitPhase phase;
    private final RateLimitPolicies policies;
    private final InMemoryRateLimiter limiter;
    private final ClientAddressResolver clientAddressResolver;
    private final ProblemDetailWriter problemDetailWriter;

    /**
     * @param phase                 point of the chain this instance occupies
     * @param policies              configured policies
     * @param limiter               counter store
     * @param clientAddressResolver resolver of the address to attribute a request to
     * @param problemDetailWriter   writer of the rejection body
     */
    public RateLimitFilter(RateLimitPhase phase, RateLimitPolicies policies, InMemoryRateLimiter limiter,
            ClientAddressResolver clientAddressResolver, ProblemDetailWriter problemDetailWriter) {
        this.phase = phase;
        this.policies = policies;
        this.limiter = limiter;
        this.clientAddressResolver = clientAddressResolver;
        this.problemDetailWriter = problemDetailWriter;
    }

    /** The phase this instance applies. */
    public RateLimitPhase phase() {
        return phase;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        RateLimitProperties.Route route = policies.find(request, phase);
        if (route == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = route.id() + '|' + subjectKey(request);
        RateLimitDecision decision = limiter.tryConsume(key, route.limit(), route.window());
        if (decision.allowed()) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(Math.max(1, decision.retryAfter().toSeconds())));
        problemDetailWriter.write(request, response, ApiErrorType.RATE_LIMIT_EXCEEDED);
    }

    /**
     * Identity the counter is kept for.
     *
     * <p>Before authentication there is no account to key on, so the client address is
     * used. After authentication the account is preferred, and the address remains the
     * fallback for a route that turned out to be reachable anonymously.
     */
    private String subjectKey(HttpServletRequest request) {
        if (phase == RateLimitPhase.AFTER_AUTHENTICATION) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()
                    && !(authentication instanceof org.springframework.security.authentication
                            .AnonymousAuthenticationToken)) {
                return ACCOUNT_KEY_PREFIX + authentication.getName();
            }
        }
        return ADDRESS_KEY_PREFIX + clientAddressResolver.resolve(request);
    }

    /**
     * Distinguishes the two instances of this filter.
     *
     * <p>{@link OncePerRequestFilter} guards on an attribute derived from the filter
     * name. Without this override both instances would share the attribute and the
     * second one would never run.
     */
    @Override
    protected String getAlreadyFilteredAttributeName() {
        return getClass().getName() + '.' + phase.name() + ALREADY_FILTERED_SUFFIX;
    }
}
