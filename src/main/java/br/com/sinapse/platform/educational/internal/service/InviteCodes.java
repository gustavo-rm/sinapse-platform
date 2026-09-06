package br.com.sinapse.platform.educational.internal.service;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * Generation and normalisation of invite codes.
 *
 * <p>Ten characters of Crockford base32, which is the alphabet without {@code I}, {@code L},
 * {@code O} and {@code U}. Roughly fifty bits. The excluded letters are not arbitrary: the
 * first three are the ones people confuse with digits when reading a code off a screen or a
 * whiteboard, and {@code U} is left out so that a random draw cannot spell something the
 * teacher has to apologise for.
 *
 * <p><strong>Reading is forgiving, writing is canonical.</strong> A code is generated in
 * uppercase from the alphabet and stored exactly as generated. A code being <em>presented</em>
 * for redemption is normalised first: case is folded, separators are dropped, and the
 * confusable characters are mapped the way Crockford specifies — {@code I} and {@code L}
 * become {@code 1}, {@code O} becomes {@code 0}. That mapping is unambiguous precisely because
 * those letters were excluded, so a student who typed the letter can only have meant the
 * digit. The alternative is refusing a code that was read correctly and typed the way it
 * looked, which is a support ticket rather than a security measure.
 *
 * <p>{@code U} has no mapping. It is not a confusable, so a code containing one was simply
 * mistyped and will not be found.
 */
public final class InviteCodes {

    /** Crockford base32: the digits and the letters, less I, L, O and U. */
    private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();

    /** Ten characters over a 32-symbol alphabet: 50 bits. */
    private static final int LENGTH = 10;

    /** Characters a reader may have typed for something else, and what they meant. */
    private static final String CONFUSABLE = "ILO";

    private static final char[] INTENDED = {'1', '1', '0'};

    /** Separators a person might add to make a code readable. */
    private static final String SEPARATORS = " -\t";

    private static final SecureRandom RANDOM = new SecureRandom();

    private InviteCodes() {
    }

    /**
     * Draws a code.
     *
     * <p>Rejection is not needed: the alphabet is exactly 32 symbols, so five bits map onto it
     * without bias.
     *
     * @return ten characters of the alphabet, uppercase
     */
    public static String generate() {
        StringBuilder code = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            code.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return code.toString();
    }

    /**
     * Turns what somebody typed into the form a code is stored in.
     *
     * <p>Never rejects. A value that is not a code normalises to something that is not in the
     * table, and the lookup answers the same way it does for a code that was never issued —
     * which is the answer a guesser has to get either way.
     *
     * @param presented what the client sent, possibly {@code null}
     * @return the canonical form, possibly empty
     */
    public static String normalise(String presented) {
        if (presented == null) {
            return "";
        }
        StringBuilder canonical = new StringBuilder(presented.length());
        for (char character : presented.trim().toUpperCase(Locale.ROOT).toCharArray()) {
            if (SEPARATORS.indexOf(character) >= 0) {
                continue;
            }
            int confusable = CONFUSABLE.indexOf(character);
            canonical.append(confusable >= 0 ? INTENDED[confusable] : character);
        }
        return canonical.toString();
    }
}
