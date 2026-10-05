package br.com.sinapse.platform.shared.ratelimit;

import java.util.Arrays;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Warns at startup when a deployed instance trusts no proxy.
 *
 * <p>An empty {@code sinapse.rate-limit.trusted-proxies} is the safe default and stays one:
 * believing {@code X-Forwarded-For} from anyone would let every client choose its own address.
 * But behind a reverse proxy the empty list has a cost that nothing else makes visible. Every
 * request is attributed to its direct peer, which is the proxy, so every anonymous client shares
 * one rate-limit counter per route, and consents and sessions record the proxy's address as
 * the client's. Whether a proxy is in front is something only the deployment knows, so this
 * says so once and never refuses to start.
 *
 * <p>"Deployed" means no development profile is active: production runs on the base
 * configuration with no profile. The catalogue command line is not a web application and has no
 * client addresses to attribute, so it is left alone.
 */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class TrustedProxiesStartupCheck {

    /** Profiles under which the instance is a workstation or a test, never a deployment. */
    static final Set<String> DEVELOPMENT_PROFILES = Set.of("local", "test");

    private static final Logger LOG = LoggerFactory.getLogger(TrustedProxiesStartupCheck.class);

    /**
     * @param properties  rate limiting configuration, which holds the trusted proxy list
     * @param environment the active profiles
     */
    public TrustedProxiesStartupCheck(RateLimitProperties properties, Environment environment) {
        boolean development = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(DEVELOPMENT_PROFILES::contains);
        if (!development && properties.trustedProxies().isEmpty()) {
            LOG.warn("sinapse.rate-limit.trusted-proxies is empty, so X-Forwarded-For is ignored and "
                    + "each request is attributed to its direct peer. Behind a reverse proxy that "
                    + "peer is the proxy: all anonymous clients then share one rate-limit counter "
                    + "per route, and consents and sessions record the proxy's address. Configure "
                    + "the proxy's address if one is in front (docs/PROXY_REVERSO.md).");
        }
    }
}
