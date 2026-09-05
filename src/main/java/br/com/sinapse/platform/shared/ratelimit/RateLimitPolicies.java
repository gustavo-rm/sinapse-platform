package br.com.sinapse.platform.shared.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * The configured policies, with their path patterns compiled once at startup.
 */
@Component
public class RateLimitPolicies {

    private final List<CompiledPolicy> policies;

    /**
     * @param properties rate limiting configuration
     */
    public RateLimitPolicies(RateLimitProperties properties) {
        PathPatternParser parser = PathPatternParser.defaultInstance;
        this.policies = properties.routes().stream()
                .map(route -> new CompiledPolicy(route, parser.parse(route.path())))
                .toList();
    }

    /**
     * Finds the policy that governs a request at a given point of the chain.
     *
     * <p>Declaration order decides: the first matching route wins, so a specific path
     * can be declared above a broader one.
     *
     * @param request current request
     * @param phase   point of the chain asking
     * @return the policy, or {@code null} when the route is not limited at this phase
     */
    public RateLimitProperties.Route find(HttpServletRequest request, RateLimitPhase phase) {
        PathContainer path = PathContainer.parsePath(requestPath(request));
        for (CompiledPolicy policy : policies) {
            if (policy.route().phase() == phase && policy.pattern().matches(path)) {
                return policy.route();
            }
        }
        return null;
    }

    private static String requestPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            String withoutContext = uri.substring(contextPath.length());
            return withoutContext.isEmpty() ? "/" : withoutContext;
        }
        return uri;
    }

    private record CompiledPolicy(RateLimitProperties.Route route, PathPattern pattern) {
    }
}
