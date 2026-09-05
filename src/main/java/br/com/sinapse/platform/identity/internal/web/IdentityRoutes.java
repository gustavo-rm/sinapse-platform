package br.com.sinapse.platform.identity.internal.web;

import br.com.sinapse.platform.shared.web.ApiPaths;

/**
 * The routes of the identity module, in one place.
 *
 * <p>They are declared here rather than spelled out twice because the security chain and
 * the controllers have to agree on them exactly. A route protected under one spelling and
 * mapped under another is open, and nothing about the code would look wrong.
 */
public final class IdentityRoutes {

    /** Registration. */
    public static final String ACCOUNTS = ApiPaths.V1 + "/accounts";

    /** Consumption of an e-mail verification token. */
    public static final String EMAIL_VERIFICATIONS = ApiPaths.V1 + "/email-verifications";

    /** Authentication, and the collection of the caller's own sessions. */
    public static final String SESSIONS = ApiPaths.V1 + "/sessions";

    /** Any single session of the caller. */
    public static final String SESSIONS_ANY = SESSIONS + "/**";

    /** Request of a password reset. */
    public static final String PASSWORD_RESETS = ApiPaths.V1 + "/password-resets";

    /** Completion of a password reset against its token. */
    public static final String PASSWORD_RESET_CONFIRMATION = PASSWORD_RESETS + "/confirmation";

    /** Change of password by a signed-in holder. */
    public static final String PASSWORD_CHANGES = ApiPaths.V1 + "/password-changes";

    /** The caller's own consents. */
    public static final String CONSENTS = ApiPaths.V1 + "/consents";

    /** Any single consent of the caller. */
    public static final String CONSENTS_ANY = CONSENTS + "/**";

    /** Reaffirmation of consent after reaching the age threshold. */
    public static final String CONSENT_REAFFIRMATION = CONSENTS + "/reaffirmation";

    /** Published wordings of the consent terms. */
    public static final String TERMS = ApiPaths.V1 + "/terms";

    private IdentityRoutes() {
    }
}
