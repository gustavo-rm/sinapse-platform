package br.com.sinapse.platform.shared.ratelimit;

/**
 * Point of the filter chain at which a policy is applied.
 *
 * <p>The choice is not cosmetic. An anonymous route must be throttled before any
 * authentication work happens, so that an unauthenticated flood is rejected cheaply.
 * An authenticated route must be throttled after authentication, because only then is
 * the account identifier — the key the policy needs — known.
 */
public enum RateLimitPhase {

    /** Applied before authentication. Keyed by client address. */
    BEFORE_AUTHENTICATION,

    /** Applied after authentication. Keyed by account, falling back to client address. */
    AFTER_AUTHENTICATION
}
