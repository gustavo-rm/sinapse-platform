package br.com.sinapse.platform.planning.internal.web;

import br.com.sinapse.platform.shared.web.ApiPaths;

/**
 * The routes of the planning module, in one place.
 *
 * <p>Declared here rather than spelled out twice because the security chain and the
 * controllers have to agree on them exactly. A route protected under one spelling and mapped
 * under another is an open route, and nothing about the code would look wrong.
 */
public final class PlanningRoutes {

    /** The caller's weekly availability. */
    public static final String AVAILABILITY = ApiPaths.V1 + "/availability";

    /** Anything under one availability window. */
    public static final String AVAILABILITY_ANY = AVAILABILITY + "/**";

    /** The caller's goals. */
    public static final String GOALS = ApiPaths.V1 + "/goals";

    /** Anything under one goal. */
    public static final String GOALS_ANY = GOALS + "/**";

    /** The caller's plans. */
    public static final String STUDY_PLANS = ApiPaths.V1 + "/study-plans";

    /** Anything under one plan. */
    public static final String STUDY_PLANS_ANY = STUDY_PLANS + "/**";

    /** The plan currently in force. */
    public static final String CURRENT_PLAN = STUDY_PLANS + "/current";

    /** Plan generation jobs. Creation is rate limited, per account. */
    public static final String GENERATION_REQUESTS = STUDY_PLANS + "/generation-requests";

    /** The caller's scheduled sessions, over a date range. */
    public static final String PLANNED_SESSIONS = ApiPaths.V1 + "/planned-sessions";

    private PlanningRoutes() {
    }
}
