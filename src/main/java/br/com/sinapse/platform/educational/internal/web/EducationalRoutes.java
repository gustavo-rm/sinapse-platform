package br.com.sinapse.platform.educational.internal.web;

import br.com.sinapse.platform.shared.web.ApiPaths;

/**
 * The routes of the educational module, in one place.
 *
 * <p>Declared here rather than spelled out twice because the security chain and the
 * controllers have to agree on them exactly. A route protected under one spelling and mapped
 * under another is an open route, and nothing about the code would look wrong.
 */
public final class EducationalRoutes {

    /** A teacher's classrooms. */
    public static final String CLASSROOMS = ApiPaths.V1 + "/classrooms";

    /** Anything under one classroom. */
    public static final String CLASSROOMS_ANY = CLASSROOMS + "/**";

    /** Invites, addressed by identifier or by code. */
    public static final String INVITES = ApiPaths.V1 + "/invites";

    /** Anything under one invite, including the preview and the redemption. */
    public static final String INVITES_ANY = INVITES + "/**";

    /** Redemption of a code. Rate limited on both the origin and the account. */
    public static final String INVITE_REDEMPTIONS = INVITES + "/*/redemptions";

    /** Preview of a code, shown before the student decides. */
    public static final String INVITE_PREVIEWS = INVITES + "/*/preview";

    /** The caller's own memberships. */
    public static final String ENROLLMENTS = ApiPaths.V1 + "/enrollments";

    /** One membership of the caller. */
    public static final String ENROLLMENTS_ANY = ENROLLMENTS + "/**";

    private EducationalRoutes() {
    }
}
