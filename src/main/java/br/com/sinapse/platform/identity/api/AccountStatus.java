package br.com.sinapse.platform.identity.api;

/**
 * States an account can be in, as drawn in section 5.2 of the architecture document.
 *
 * <p>The adult and the minor walk the same path. The only difference is that the consent
 * record of an adult is granted by the holder and the transition to {@link #ACTIVE} is
 * immediate, while a minor waits in {@link #PENDING_GUARDIAN_CONSENT} for a guardian.
 */
public enum AccountStatus {

    /** Registered, e-mail not verified yet. */
    PENDING_VERIFICATION,

    /**
     * E-mail verified, but the holder was below the consent age threshold, so the account
     * waits for a guardian. Not reachable in v1: registration below the threshold is
     * refused, and the guardian verification flow belongs to the next version.
     */
    PENDING_GUARDIAN_CONSENT,

    /** Usable. Requires a valid consent record for every essential purpose. */
    ACTIVE,

    /**
     * Suspended, by revocation of an essential consent, by an expired majority grace
     * period, or by decision. Sessions of a suspended account are revoked in the same
     * transaction that suspends it.
     */
    SUSPENDED,

    /** Terminal state of Article 18 erasure. An anonymised account never comes back. */
    ANONYMIZED;

    /** Whether the account can still change state. */
    public boolean isTerminal() {
        return this == ANONYMIZED;
    }
}
