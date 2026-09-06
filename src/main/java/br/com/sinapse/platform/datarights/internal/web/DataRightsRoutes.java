package br.com.sinapse.platform.datarights.internal.web;

import br.com.sinapse.platform.shared.web.ApiPaths;

/**
 * The routes of the data subject rights coordinator, in one place.
 *
 * <p>Under {@code /me}, unlike most of this API, and deliberately: these are not operations on a
 * collection the holder happens to own, they are the holder acting on themselves. Reading them
 * any other way would invite a path parameter, and a path parameter here is a route that erases
 * somebody else.
 */
public final class DataRightsRoutes {

    /** Anything the holder does about their own data. */
    public static final String ME = ApiPaths.V1 + "/me";

    /** The caller's erasure requests. */
    public static final String ERASURE_REQUESTS = ME + "/erasure-requests";

    /** Anything under one erasure request. */
    public static final String ERASURE_REQUESTS_ANY = ERASURE_REQUESTS + "/**";

    /** Everything the platform holds about the caller. Rate limited, per account. */
    public static final String DATA_EXPORT = ME + "/data-export";

    /** Who could read the caller's data, and when. */
    public static final String ACCESS_DISCLOSURE = ME + "/access-disclosure";

    private DataRightsRoutes() {
    }
}
