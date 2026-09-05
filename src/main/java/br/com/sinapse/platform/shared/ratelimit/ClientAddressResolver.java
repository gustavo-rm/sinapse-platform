package br.com.sinapse.platform.shared.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

/**
 * Determines the address a request really came from.
 *
 * <p>The address used for rate limiting is the one the infrastructure reports,
 * {@code ServletRequest#getRemoteAddr()}. {@code X-Forwarded-For} is consulted only
 * when that address belongs to a configured trusted proxy; otherwise the header is
 * ignored entirely, because any client can send it.
 *
 * <p>When the direct peer is trusted, the header is walked from the right, discarding
 * addresses that are themselves trusted proxies. The first untrusted address found is
 * the client; everything to its left was appended by whoever the client is and cannot
 * be believed.
 */
@Component
public class ClientAddressResolver {

    /** Header a reverse proxy uses to report the original client. */
    private static final String X_FORWARDED_FOR = "X-Forwarded-For";

    /** Value reported when the container gives no address at all. */
    private static final String UNKNOWN_ADDRESS = "unknown";

    private final List<IpAddressMatcher> trustedProxies;

    /**
     * @param properties rate limiting configuration, source of the trusted proxy list
     * @throws IllegalArgumentException if a configured entry is not a valid address or
     *                                  CIDR block, so that a typo fails at startup rather
     *                                  than silently widening trust
     */
    public ClientAddressResolver(RateLimitProperties properties) {
        List<IpAddressMatcher> matchers = new ArrayList<>();
        for (String trustedProxy : properties.trustedProxies()) {
            matchers.add(new IpAddressMatcher(trustedProxy.trim()));
        }
        this.trustedProxies = Collections.unmodifiableList(matchers);
    }

    /**
     * @param request current request
     * @return the address to attribute the request to
     */
    public String resolve(HttpServletRequest request) {
        String directPeer = request.getRemoteAddr();
        if (directPeer == null || !isTrustedProxy(directPeer)) {
            return directPeer == null ? UNKNOWN_ADDRESS : directPeer;
        }
        List<String> forwarded = forwardedFor(request);
        for (int i = forwarded.size() - 1; i >= 0; i--) {
            String candidate = forwarded.get(i);
            if (!isTrustedProxy(candidate)) {
                return candidate;
            }
        }
        return directPeer;
    }

    private boolean isTrustedProxy(String address) {
        for (IpAddressMatcher matcher : trustedProxies) {
            try {
                if (matcher.matches(address)) {
                    return true;
                }
            } catch (IllegalArgumentException notAnAddress) {
                // A value that is not an address cannot be a trusted proxy. Obfuscated
                // and malformed entries are common in forwarding headers.
                return false;
            }
        }
        return false;
    }

    private static List<String> forwardedFor(HttpServletRequest request) {
        List<String> addresses = new ArrayList<>();
        Enumeration<String> headers = request.getHeaders(X_FORWARDED_FOR);
        if (headers == null) {
            return addresses;
        }
        while (headers.hasMoreElements()) {
            for (String value : headers.nextElement().split(",")) {
                String address = value.trim();
                if (!address.isEmpty()) {
                    addresses.add(address);
                }
            }
        }
        return addresses;
    }
}
