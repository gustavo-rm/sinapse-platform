package br.com.sinapse.platform.shared.web;

import java.time.Duration;
import java.time.Instant;

/**
 * Whether a requested time window is one the server will answer.
 *
 * <p>Section 1 of the API contract settles this once: histories and schedules grow without
 * limit, so their window is mandatory and its span is capped, and there is no generic
 * pagination in this API to fall back on. Cursor paging is a cheap addition once a concrete
 * case asks for one; building it now would be speculative.
 *
 * <p>The rule is here rather than in each module because it is the same rule everywhere. What
 * is not the same is the ceiling — a study history and a plan's schedule accumulate at
 * different rates — so the span is an argument, read by each module from its own
 * configuration, and each module raises its own failure.
 *
 * <p>Windows are half-open: {@code from} inclusive, {@code to} exclusive. That is why an empty
 * window is refused rather than answered with nothing — a client asking for a window that
 * cannot contain anything has made a mistake, and an empty list would hide it behind a
 * plausible result.
 */
public final class TimeWindows {

    private TimeWindows() {
    }

    /**
     * Whether a window is non-empty, correctly ordered and within the span allowed.
     *
     * @param from    start, inclusive
     * @param to      end, exclusive
     * @param maxSpan widest window that will be answered
     * @return whether the window may be answered
     */
    public static boolean isAcceptable(Instant from, Instant to, Duration maxSpan) {
        return to.isAfter(from) && Duration.between(from, to).compareTo(maxSpan) <= 0;
    }
}
