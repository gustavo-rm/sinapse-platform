package br.com.sinapse.platform.learningrecord.internal.web;

import br.com.sinapse.platform.shared.web.ApiPaths;

/**
 * The routes of the learning record module, in one place.
 *
 * <p>Declared here rather than spelled out twice because the security chain and the
 * controllers have to agree on them exactly. A route protected under one spelling and mapped
 * under another is an open route, and nothing about the code would look wrong.
 */
public final class LearningRecordRoutes {

    /** The caller's own study sessions. */
    public static final String SESSIONS = ApiPaths.V1 + "/study-sessions";

    /** Anything under the caller's sessions. */
    public static final String SESSIONS_ANY = SESSIONS + "/**";

    /** The session the caller currently has running, if any. */
    public static final String CURRENT_SESSION = SESSIONS + "/current";

    /** Sessions entered after the fact. Rate limited, per account. */
    public static final String RETROACTIVE_ENTRIES = SESSIONS + "/retroactive-entries";

    private LearningRecordRoutes() {
    }
}
