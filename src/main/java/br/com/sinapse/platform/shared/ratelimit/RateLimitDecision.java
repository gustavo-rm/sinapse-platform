package br.com.sinapse.platform.shared.ratelimit;

import java.time.Duration;

/**
 * Outcome of a rate limit check.
 *
 * @param allowed    whether the request may proceed
 * @param retryAfter time remaining until the current window closes
 */
public record RateLimitDecision(boolean allowed, Duration retryAfter) {

    private static final RateLimitDecision ACCEPTED = new RateLimitDecision(true, Duration.ZERO);

    /** The request fits within the window. */
    public static RateLimitDecision accept() {
        return ACCEPTED;
    }

    /**
     * The window is exhausted.
     *
     * @param retryAfter time remaining until it reopens
     */
    public static RateLimitDecision reject(Duration retryAfter) {
        return new RateLimitDecision(false, retryAfter);
    }
}
