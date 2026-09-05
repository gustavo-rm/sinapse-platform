package br.com.sinapse.platform.identity.api;

/**
 * Roles an account may hold.
 *
 * <p>Registration grants {@link #STUDENT} and nothing else. How an account comes to hold
 * {@link #TEACHER} is an open product decision (P2 in {@code CLAUDE.md}); until it is
 * answered, no code path grants it.
 */
public enum AccountRole {

    /** Holder of learning data. Granted at registration. */
    STUDENT,

    /** Owner of classrooms. Not granted by any code path yet — see P2. */
    TEACHER,

    /** Curator of the catalogue and operator of administrative routes. */
    ADMIN;

    /** Authority name Spring Security knows this role by. */
    public String authority() {
        return "ROLE_" + name();
    }
}
