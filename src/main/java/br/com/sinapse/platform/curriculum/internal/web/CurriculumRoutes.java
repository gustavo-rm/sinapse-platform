package br.com.sinapse.platform.curriculum.internal.web;

import br.com.sinapse.platform.shared.web.ApiPaths;

/**
 * The routes of the curriculum module, in one place.
 *
 * <p>Declared here rather than spelled out twice because the security chain and the
 * controller have to agree on them exactly. A route protected under one spelling and mapped
 * under another is an open route, and nothing about the code would look wrong.
 */
public final class CurriculumRoutes {

    /** The subjects of the catalogue. */
    public static final String SUBJECTS = ApiPaths.V1 + "/subjects";

    /** Anything under one subject. */
    public static final String SUBJECTS_ANY = SUBJECTS + "/**";

    private CurriculumRoutes() {
    }
}
