package br.com.sinapse.platform.identity.internal.web;

/**
 * The bounds a password has to fall within.
 *
 * <p>Length and nothing else. Composition rules — a digit, a symbol, a capital — push people
 * towards short predictable passwords and are no longer recommended by anyone who measures
 * the result; length is what the hash actually benefits from. Twelve is a floor, not advice.
 *
 * <p>The upper bound is not about Argon2, which has none. It bounds the work a single
 * unauthenticated request can ask the server to do.
 */
public final class PasswordPolicy {

    /** Shortest password accepted. */
    public static final int MIN_LENGTH = 12;

    /** Longest password accepted. */
    public static final int MAX_LENGTH = 256;

    private PasswordPolicy() {
    }
}
