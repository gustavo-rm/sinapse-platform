package br.com.sinapse.platform.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.sinapse.platform.IntegrationTest;
import br.com.sinapse.platform.shared.ratelimit.RateLimitFilter;
import br.com.sinapse.platform.shared.ratelimit.RateLimitPhase;
import jakarta.servlet.Filter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.header.HeaderWriterFilter;
import org.springframework.web.context.WebApplicationContext;

/**
 * ADR 0009 states that rate limiting runs before authentication for anonymous routes and
 * after it for authenticated ones, and that the position is a decision rather than an
 * accident of configuration. A decision that nothing checks is a comment, so this test
 * reads the chain Spring Security actually assembled and asserts the two positions.
 */
class FilterOrderIntegrationTest extends IntegrationTest {

    private static final String API_ROUTE = "/api/v1/probe/ping";

    @Autowired
    private FilterChainProxy filterChainProxy;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Test
    void rateLimitingSurroundsAuthentication() {
        List<Filter> chain = apiChain();

        int headerWriter = indexOf(chain, HeaderWriterFilter.class);
        int beforeAuthentication = indexOfRateLimit(chain, RateLimitPhase.BEFORE_AUTHENTICATION);
        int authentication = indexOf(chain, AnonymousAuthenticationFilter.class);
        int afterAuthentication = indexOfRateLimit(chain, RateLimitPhase.AFTER_AUTHENTICATION);
        int authorization = indexOf(chain, AuthorizationFilter.class);

        assertThat(beforeAuthentication)
                .as("an anonymous flood must be rejected before any authentication work")
                .isGreaterThan(headerWriter)
                .isLessThan(authentication);

        assertThat(afterAuthentication)
                .as("keying a counter by account requires the security context to be populated")
                .isGreaterThan(authentication)
                .isLessThan(authorization);
    }

    /**
     * The chain Spring Security selects for an API route, as opposed to the one that
     * serves the management endpoints.
     */
    private List<Filter> apiChain() {
        // The management chain matches on the actuator endpoints, which it looks up
        // through the servlet context, so the request has to carry it.
        MockHttpServletRequest request =
                new MockHttpServletRequest(webApplicationContext.getServletContext(), "GET", API_ROUTE);
        return filterChainProxy.getFilterChains().stream()
                .filter(candidate -> candidate.matches(request))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No security filter chain matches " + API_ROUTE))
                .getFilters();
    }

    private static int indexOfRateLimit(List<Filter> chain, RateLimitPhase phase) {
        for (int index = 0; index < chain.size(); index++) {
            if (chain.get(index) instanceof RateLimitFilter filter && filter.phase() == phase) {
                return index;
            }
        }
        throw new AssertionError("No rate limit filter registered for phase " + phase);
    }

    private static int indexOf(List<Filter> chain, Class<? extends Filter> type) {
        for (int index = 0; index < chain.size(); index++) {
            if (type.isInstance(chain.get(index))) {
                return index;
            }
        }
        throw new AssertionError("No filter of type " + type.getSimpleName() + " in the chain");
    }
}
