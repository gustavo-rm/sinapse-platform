package br.com.sinapse.platform.identity.internal.domain;

/**
 * What was recorded about the act of consenting, stored as the {@code evidence} document.
 *
 * <p>ADR 0004: this is personal data collected to comply with a legal obligation — the
 * burden of proving consent is the controller's — and not under consent itself. It has a
 * retention policy of its own, still to be defined (J3).
 *
 * <p>The date of birth is self-declared and cannot be verified. That is the standard
 * practice and is compatible with a duty of reasonable effort rather than of documentary
 * proof, but the effort has to be demonstrable, which is why the method is recorded here
 * instead of being assumed.
 *
 * <p>Only strings, and no timestamp: the instant of the act is {@code granted_at} on the
 * record itself, and duplicating it in a document would create two answers to one
 * question.
 *
 * @param ipAddress             address the act came from, as resolved from the
 *                              infrastructure rather than from a header the client
 *                              controls
 * @param userAgent             agent string sent by the client, truncated
 * @param collectionMethod      how the consent was collected
 * @param ageVerificationMethod how the holder's age was established
 */
public record ConsentEvidence(
        String ipAddress,
        String userAgent,
        String collectionMethod,
        String ageVerificationMethod) {

    /** Consent collected through a form of the platform's own API. */
    public static final String METHOD_API_FORM = "API_FORM";

    /** Age taken from the date of birth the holder declared. */
    public static final String AGE_SELF_DECLARED = "SELF_DECLARED_DATE_OF_BIRTH";

    /** Longest user agent kept. Anything beyond this adds storage and no evidence. */
    private static final int MAX_USER_AGENT = 512;

    /**
     * Records an act of consent collected through the API.
     *
     * @param ipAddress address the request came from
     * @param userAgent agent string, possibly {@code null}
     * @return the evidence document
     */
    public static ConsentEvidence ofApiForm(String ipAddress, String userAgent) {
        return new ConsentEvidence(ipAddress, truncate(userAgent), METHOD_API_FORM, AGE_SELF_DECLARED);
    }

    private static String truncate(String userAgent) {
        if (userAgent == null) {
            return null;
        }
        return userAgent.length() <= MAX_USER_AGENT ? userAgent : userAgent.substring(0, MAX_USER_AGENT);
    }
}
