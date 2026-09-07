package br.com.sinapse.platform.readmodel.internal.web;

import br.com.sinapse.platform.shared.web.ApiPaths;

/**
 * The routes of the read models, in one place.
 *
 * <p>Declared here rather than spelled out twice because the security chain and the controllers
 * have to agree on them exactly. A route protected under one spelling and mapped under another
 * is an open route, and nothing about the code would look wrong.
 *
 * <p>Three of these five sit under paths another module already owns — {@code /classrooms} and
 * {@code /study-plans} — and that is deliberate: a read model is another way of looking at the
 * same resource, not a resource of its own. What it must not do is redeclare their protection;
 * see {@code ReadModelSecurityCustomizer}.
 *
 * <p>Those three carry two spellings, because the security chain matches Ant patterns and the
 * dispatcher maps URI templates, and the two syntaxes do not overlap. Both are here rather than
 * one here and one inline, so that a change to a path is a change to one file.
 */
public final class ReadModelRoutes {

    /** Everything the initial screen needs, for the caller. */
    public static final String MY_STATE = ApiPaths.V1 + "/me/state";

    /** The caller's agenda over a window. */
    public static final String MY_AGENDA = ApiPaths.V1 + "/me/agenda";

    /** A summary of one of the caller's plans, as the security chain matches it. */
    public static final String PLAN_SUMMARY = ApiPaths.V1 + "/study-plans/*/summary";

    /** The same route as the dispatcher maps it. */
    public static final String PLAN_SUMMARY_TEMPLATE = ApiPaths.V1 + "/study-plans/{planId}/summary";

    /** The students of a classroom the caller owns. Rate limited, per account. */
    public static final String CLASSROOM_STUDENTS = ApiPaths.V1 + "/classrooms/*/students";

    /** The same route as the dispatcher maps it. */
    public static final String CLASSROOM_STUDENTS_TEMPLATE =
            ApiPaths.V1 + "/classrooms/{classroomId}/students";

    /** One student's panel. Rate limited, per account. */
    public static final String STUDENT_PANEL = CLASSROOM_STUDENTS + "/*/panel";

    /** The same route as the dispatcher maps it. */
    public static final String STUDENT_PANEL_TEMPLATE = CLASSROOM_STUDENTS_TEMPLATE
            + "/{accountId}/panel";

    private ReadModelRoutes() {
    }
}
