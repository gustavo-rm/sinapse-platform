package br.com.sinapse.platform.planning.api;

/**
 * What a scheduled session is for.
 *
 * <p>The same two values the learning record uses, declared separately because neither module
 * may depend on the other (rule R2). Sharing the type would be the dependency the rule exists
 * to forbid, and moving it to {@code shared} would put a domain concept in the package that
 * holds none. The two columns carry the same check constraint, so the database is where the
 * agreement is actually kept.
 */
public enum PlannedSessionKind {

    /** New ground. */
    STUDY,

    /** Going back over something already studied. */
    REVISION
}
