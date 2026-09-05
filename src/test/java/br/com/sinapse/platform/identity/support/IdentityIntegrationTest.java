package br.com.sinapse.platform.identity.support;

import br.com.sinapse.platform.IntegrationTest;
import br.com.sinapse.platform.shared.ratelimit.InMemoryRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Base of the identity integration tests.
 *
 * <p>Each test starts from an empty population. The truncation cascades from {@code account}
 * and therefore reaches every table that references it, which is what makes an assertion
 * about "the sessions of this account" mean something.
 *
 * <p>{@code TRUNCATE} does not fire the row-level delete trigger on {@code consent_record},
 * which is the one thing that makes emptying the table possible at all — and a good reminder
 * of what that trigger does and does not defend against. The published wordings survive,
 * because they are seeded once when the context starts.
 *
 * <p>The rate limiter is reset too. Its counters live in the heap for the whole context, so
 * without this a test that exhausts a window would fail the next test to use the same route.
 */
@Import(IdentityTestSupport.class)
public abstract class IdentityIntegrationTest extends IntegrationTest {

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected IdentityFixtures fixtures;

    @Autowired
    protected CapturingAccountNotifier notifications;

    @Autowired
    private InMemoryRateLimiter rateLimiter;

    @BeforeEach
    void resetIdentityState() {
        jdbc.execute("truncate table account cascade");
        notifications.clear();
        rateLimiter.reset();
    }
}
