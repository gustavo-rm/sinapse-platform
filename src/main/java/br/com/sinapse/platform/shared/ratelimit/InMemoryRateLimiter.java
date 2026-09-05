package br.com.sinapse.platform.shared.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Fixed window counter held in the heap of a single instance.
 *
 * <p><strong>Known limit.</strong> The counters live in this process. With a single
 * executable artifact, which is the deployment shape decided in ADR 0001, this is
 * accurate. Under horizontal scaling it stops holding: each instance would count its
 * own share, and the effective limit would be multiplied by the number of instances.
 * Moving to shared storage is therefore a mandatory review point before any second
 * instance is started, and is recorded as such in ADR 0009.
 *
 * <p>The clock is injected rather than read from the system, so that windows do not
 * depend on the JVM default zone and can be driven deterministically by tests.
 */
@Component
public class InMemoryRateLimiter {

    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    /**
     * @param clock application clock
     */
    public InMemoryRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /**
     * Registers one request against a counter.
     *
     * @param key      counter identity; already scoped by policy by the caller
     * @param limit    requests accepted within the window
     * @param duration length of the window
     * @return whether the request may proceed, and when to retry if it may not
     */
    public RateLimitDecision tryConsume(String key, int limit, Duration duration) {
        Instant now = clock.instant();
        Window window = windows.compute(key, (ignored, current) -> {
            if (current == null || !now.isBefore(current.startedAt().plus(duration))) {
                return new Window(now, 1);
            }
            return new Window(current.startedAt(), current.count() + 1);
        });

        if (window.count() <= limit) {
            return RateLimitDecision.accept();
        }
        Duration remaining = Duration.between(now, window.startedAt().plus(duration));
        return RateLimitDecision.reject(remaining.isNegative() ? Duration.ZERO : remaining);
    }

    /**
     * Discards every counter.
     *
     * <p>Exists so that tests can start from a known state; nothing in production calls it.
     */
    public void reset() {
        windows.clear();
    }

    private record Window(Instant startedAt, int count) {
    }
}
