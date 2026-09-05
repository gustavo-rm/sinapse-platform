package br.com.sinapse.platform.shared.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/**
 * Window arithmetic of the limiter, driven by a clock the test controls.
 */
class InMemoryRateLimiterTest {

    private static final Instant START = Instant.parse("2026-09-04T19:00:00Z");
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final int LIMIT = 3;

    private final MutableClock clock = new MutableClock(START);
    private final InMemoryRateLimiter limiter = new InMemoryRateLimiter(clock);

    @Test
    void acceptsUpToTheLimitAndThenRejects() {
        for (int attempt = 1; attempt <= LIMIT; attempt++) {
            assertThat(limiter.tryConsume("key", LIMIT, WINDOW).allowed()).isTrue();
        }

        RateLimitDecision rejected = limiter.tryConsume("key", LIMIT, WINDOW);

        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.retryAfter()).isEqualTo(WINDOW);
    }

    @Test
    void countersAreIndependentPerKey() {
        for (int attempt = 1; attempt <= LIMIT; attempt++) {
            limiter.tryConsume("first", LIMIT, WINDOW);
        }

        assertThat(limiter.tryConsume("second", LIMIT, WINDOW).allowed()).isTrue();
    }

    @Test
    void windowReopensOnceItHasElapsed() {
        for (int attempt = 1; attempt <= LIMIT + 1; attempt++) {
            limiter.tryConsume("key", LIMIT, WINDOW);
        }

        clock.advance(WINDOW);

        assertThat(limiter.tryConsume("key", LIMIT, WINDOW).allowed()).isTrue();
    }

    @Test
    void retryAfterShrinksAsTheWindowRunsOut() {
        for (int attempt = 1; attempt <= LIMIT; attempt++) {
            limiter.tryConsume("key", LIMIT, WINDOW);
        }
        clock.advance(Duration.ofSeconds(40));

        assertThat(limiter.tryConsume("key", LIMIT, WINDOW).retryAfter())
                .isEqualTo(Duration.ofSeconds(20));
    }

    @Test
    void resetDiscardsEveryCounter() {
        for (int attempt = 1; attempt <= LIMIT + 1; attempt++) {
            limiter.tryConsume("key", LIMIT, WINDOW);
        }

        limiter.reset();

        assertThat(limiter.tryConsume("key", LIMIT, WINDOW).allowed()).isTrue();
    }

    /** Clock the test moves by hand, so that no assertion depends on wall time. */
    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration amount) {
            instant = instant.plus(amount);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
