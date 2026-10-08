package br.com.sinapse.platform.coreclient.api;

import java.time.Duration;
import java.util.Objects;

/**
 * How long one call to the core may take before the adapter gives up on it.
 *
 * <p>Published so that a caller can reason about time without knowing how the core is reached.
 * The job that calls the core needs to tell a call still in progress from one whose worker died:
 * a job that has been running for longer than any call can last has nobody left waiting for the
 * answer. The values are the adapter's own configuration, read here rather than restated by the
 * caller, so that raising a timeout raises every bound derived from it.
 *
 * @param connectTimeout how long the adapter waits for the connection to be established
 * @param readTimeout    how long it then waits for the answer
 */
public record CoreCallLimits(Duration connectTimeout, Duration readTimeout) {

    /**
     * @throws NullPointerException if either value is missing
     */
    public CoreCallLimits {
        Objects.requireNonNull(connectTimeout, "connectTimeout");
        Objects.requireNonNull(readTimeout, "readTimeout");
    }

    /**
     * The longest one call can last before the adapter abandons it: connecting, then waiting for
     * the answer.
     *
     * @return the connect timeout plus the read timeout
     */
    public Duration longestCall() {
        return connectTimeout.plus(readTimeout);
    }
}
