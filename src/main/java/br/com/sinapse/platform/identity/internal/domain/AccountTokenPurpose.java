package br.com.sinapse.platform.identity.internal.domain;

/**
 * Why a single-use token was issued to the account holder.
 *
 * <p>Guardian verification is deliberately absent: that token is delivered to a third
 * party rather than to the holder, and ADR 0010 keeps it in columns of its own on
 * {@code guardian}. Merging the two would put a third party's credential in the holder's
 * table and blur who a token was sent to.
 */
public enum AccountTokenPurpose {

    /** Proves control of the e-mail address given at registration. */
    EMAIL_VERIFICATION,

    /** Authorises setting a new password without knowing the current one. */
    PASSWORD_RESET,

    /**
     * Authorises the holder to reaffirm, in their own name, a consent a guardian had
     * granted, once the holder reaches the configured age threshold.
     */
    MAJORITY_REAFFIRMATION
}
