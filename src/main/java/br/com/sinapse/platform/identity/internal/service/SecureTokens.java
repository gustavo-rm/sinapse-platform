package br.com.sinapse.platform.identity.internal.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Generation and hashing of the opaque values this module hands out.
 *
 * <p>Two kinds of value pass through here — session tokens and single-use account tokens —
 * and both follow the same rule: the clear value exists on the client, the database holds
 * only its SHA-256 hash, and nothing ever recovers one from the other.
 *
 * <p>The hash is unsalted, deliberately. A salt defends a low-entropy secret against a
 * dictionary; these values are 256 bits from a cryptographically secure source, so there
 * is no dictionary to defend against, and a per-row salt would prevent the lookup by hash
 * that authentication is.
 */
public final class SecureTokens {

    /** 256 bits, as required by ADR 0010. */
    private static final int TOKEN_BYTES = 32;

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private SecureTokens() {
    }

    /**
     * Draws a new opaque token.
     *
     * @return 256 bits, URL-safe, without padding
     */
    public static String generate() {
        byte[] value = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(value);
        return ENCODER.encodeToString(value);
    }

    /**
     * Hashes a value for storage or lookup.
     *
     * @param value clear value presented by a client, or just generated
     * @return the SHA-256 digest, in lowercase hexadecimal
     */
    public static String hash(String value) {
        return HexFormat.of().formatHex(digest().digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            // Every Java runtime is required to provide SHA-256.
            throw new IllegalStateException("SHA-256 is unavailable in this runtime", impossible);
        }
    }
}
