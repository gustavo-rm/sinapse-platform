package br.com.sinapse.platform.shared.web;

import br.com.sinapse.platform.shared.ratelimit.RateLimitProperties;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * The forged-header bypass, in isolation.
 *
 * <p>A bypass of exactly this shape was found in the Sinapse Core repository: the limiter
 * keyed on {@code X-Forwarded-For}, so a client that varied the header never hit a limit.
 */
class ClientAddressResolverTest {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String DIRECT_PEER = "198.51.100.10";

    @Test
    void withoutTrustedProxiesTheHeaderIsIgnored() {
        ClientAddressResolver resolver = resolverTrusting();

        MockHttpServletRequest request = requestFrom(DIRECT_PEER);
        request.addHeader(X_FORWARDED_FOR, "203.0.113.1");

        assertThat(resolver.resolve(request)).isEqualTo(DIRECT_PEER);
    }

    @Test
    void headerFromAnUntrustedPeerIsIgnoredEvenWhenProxiesAreConfigured() {
        ClientAddressResolver resolver = resolverTrusting("10.0.0.0/8");

        MockHttpServletRequest request = requestFrom(DIRECT_PEER);
        request.addHeader(X_FORWARDED_FOR, "203.0.113.1");

        assertThat(resolver.resolve(request)).isEqualTo(DIRECT_PEER);
    }

    @Test
    void headerFromATrustedProxyIsBelieved() {
        ClientAddressResolver resolver = resolverTrusting("10.0.0.0/8");

        MockHttpServletRequest request = requestFrom("10.1.2.3");
        request.addHeader(X_FORWARDED_FOR, "203.0.113.1");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.1");
    }

    @Test
    void onlyTheRightmostUntrustedEntryIsTheClient() {
        ClientAddressResolver resolver = resolverTrusting("10.0.0.0/8");

        MockHttpServletRequest request = requestFrom("10.1.2.3");
        // Everything left of the entry the proxy itself appended was sent by the client.
        request.addHeader(X_FORWARDED_FOR, "203.0.113.99, 198.51.100.7");

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.7");
    }

    @Test
    void chainOfTrustedProxiesFallsBackToTheDirectPeer() {
        ClientAddressResolver resolver = resolverTrusting("10.0.0.0/8");

        MockHttpServletRequest request = requestFrom("10.1.2.3");
        request.addHeader(X_FORWARDED_FOR, "10.4.5.6, 10.7.8.9");

        assertThat(resolver.resolve(request)).isEqualTo("10.1.2.3");
    }

    @Test
    void malformedHeaderEntryIsNotMistakenForAProxy() {
        ClientAddressResolver resolver = resolverTrusting("10.0.0.0/8");

        MockHttpServletRequest request = requestFrom("10.1.2.3");
        request.addHeader(X_FORWARDED_FOR, "not-an-address");

        assertThat(resolver.resolve(request)).isEqualTo("not-an-address");
    }

    private static ClientAddressResolver resolverTrusting(String... proxies) {
        return new ClientAddressResolver(new RateLimitProperties(List.of(proxies), List.of()));
    }

    private static MockHttpServletRequest requestFrom(String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/probe/rate-limited");
        request.setRemoteAddr(remoteAddress);
        return request;
    }
}
