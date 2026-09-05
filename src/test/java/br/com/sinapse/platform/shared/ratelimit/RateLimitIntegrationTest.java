package br.com.sinapse.platform.shared.ratelimit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.sinapse.platform.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Exercises the rate limiter against the running chain.
 *
 * <p>The test profile declares a policy of three requests per minute on
 * {@code /api/v1/probe/rate-limited}, applied before authentication.
 */
class RateLimitIntegrationTest extends IntegrationTest {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String LIMITED_ROUTE = "/api/v1/probe/rate-limited";
    private static final String UNLIMITED_ROUTE = "/api/v1/probe/ping";
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
    void burstIsBlockedOnceTheWindowIsExhausted() throws Exception {
        for (int attempt = 1; attempt <= CONFIGURED_LIMIT; attempt++) {
            mockMvc.perform(get(LIMITED_ROUTE)).andExpect(status().isOk());
        }

        mockMvc.perform(get(LIMITED_ROUTE))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:sinapse:problem:rate-limit-exceeded"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.instance").value(LIMITED_ROUTE))
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
    }

    @Test
    void forgedForwardedForDoesNotResetTheCounter() throws Exception {
        for (int attempt = 1; attempt <= CONFIGURED_LIMIT; attempt++) {
            mockMvc.perform(get(LIMITED_ROUTE)).andExpect(status().isOk());
        }

        // No trusted proxy is configured, so the header is the client's own invention.
        // Each of these would be a fresh counter if the header were believed.
        mockMvc.perform(get(LIMITED_ROUTE).header(X_FORWARDED_FOR, "203.0.113.7"))
                .andExpect(status().isTooManyRequests());
        mockMvc.perform(get(LIMITED_ROUTE).header(X_FORWARDED_FOR, "198.51.100.4"))
                .andExpect(status().isTooManyRequests());
        mockMvc.perform(get(LIMITED_ROUTE)
                        .header(X_FORWARDED_FOR, "198.51.100.5, 203.0.113.9"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void routesWithoutAPolicyAreNotLimited() throws Exception {
        for (int attempt = 1; attempt <= CONFIGURED_LIMIT * 3; attempt++) {
            mockMvc.perform(get(UNLIMITED_ROUTE)).andExpect(status().isOk());
        }
    }
}
