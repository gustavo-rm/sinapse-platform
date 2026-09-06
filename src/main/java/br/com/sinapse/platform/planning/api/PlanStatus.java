package br.com.sinapse.platform.planning.api;

/**
 * Where a plan stands.
 *
 * <p>There is no third state. A plan is the one in force or it is one that was replaced, and
 * re-planning is what moves it from the first to the second — plans are never edited and
 * never deleted (ADR 0007). The chain of superseded plans is experimental data about how
 * often and at what point in the horizon a student re-plans, obtained for nothing.
 */
public enum PlanStatus {

    /** The plan in force. At most one per account, by partial index. */
    ACTIVE,

    /** Replaced by a later plan, which it points at. */
    SUPERSEDED
}
