package br.com.sinapse.platform.identity.api;

/**
 * Who granted a consent.
 *
 * <p>ADR 0004: being of age is not the absence of consent, it is consent whose grantor is
 * the holder. Every account has consent records; this is the only field that differs
 * between a minor and an adult.
 */
public enum ConsentGrantedBy {

    /** The account holder. */
    SELF,

    /** A guardian, because the holder was below the configured age threshold. */
    GUARDIAN
}
