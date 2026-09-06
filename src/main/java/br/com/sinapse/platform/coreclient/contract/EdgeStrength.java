package br.com.sinapse.platform.coreclient.contract;

/**
 * How binding a prerequisite is.
 *
 * <p>The core applies a different mechanism to each: a hard edge is a constraint, a soft one
 * is a preference. Sending only the pair of topics would leave it unable to tell them apart,
 * and treating every edge as hard makes most real curricula infeasible.
 */
public enum EdgeStrength {

    /** Must be studied before. A violation makes the plan invalid. */
    HARD,

    /** Better studied before. A violation costs, and is sometimes worth paying. */
    SOFT
}
