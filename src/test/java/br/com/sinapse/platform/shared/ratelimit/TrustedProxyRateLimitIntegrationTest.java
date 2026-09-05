package br.com.sinapse.platform.shared.ratelimit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.sinapse.platform.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The other half of the forwarding rule: when the request really does arrive from a
 * configured trusted proxy, {@code X-Forwarded-For} is the only way to tell two clients
 * apart, and it is honoured.
 *
 * <p>Kept apart from {@link RateLimitIntegrationTest} because it needs a different
 * trusted proxy list, and the list is read once when the resolver is built.
 */
@TestPropertySource(properties = "sinapse.rate-limit.trusted-proxies=127.0.0.1/32")
class TrustedProxyRateLimitIntegrationTest extends IntegrationTest {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String LIMITED_ROUTE = "/api/v1/probe/rate-limited";
    private static final int CONFIGURED_LIMIT = 3;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InMemoryRateLimiter limiter;

    @BeforeEach
    void clearCounters() {
        limiter.reset();
    }

    @Test
    void distinctClientsBehindATrustedProxyGetDistinctCounters() throws Exception {
        for (int attempt = 1; attempt <= CONFIGURED_LIMIT; attempt++) {
            mockMvc.perform(get(LIMITED_ROUTE).header(X_FORWARDED_FOR, "203.0.113.7"))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get(LIMITED_ROUTE).header(X_FORWARDED_FOR, "203.0.113.7"))
                .andExpect(status().isTooManyRequests());

        mockMvc.perform(get(LIMITED_ROUTE).header(X_FORWARDED_FOR, "198.51.100.4"))
                .andExpect(status().isOk());
    }

    @Test
    void addressesAppendedByTheClientItselfAreIgnored() throws Exception {
        // The proxy appends the peer it saw to whatever the client sent. Only the
        // right-most untrusted entry is the real client; the rest is client-supplied.
        for (int attempt = 1; attempt <= CONFIGURED_LIMIT; attempt++) {
            mockMvc.perform(get(LIMITED_ROUTE)
                            .header(X_FORWARDED_FOR, "10.0.0." + attempt + ", 203.0.113.7"))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get(LIMITED_ROUTE)
                        .header(X_FORWARDED_FOR, "10.0.0.250, 203.0.113.7"))
                .andExpect(status().isTooManyRequests());
    }
}
