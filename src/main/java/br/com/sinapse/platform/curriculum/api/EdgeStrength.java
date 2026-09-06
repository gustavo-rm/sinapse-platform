package br.com.sinapse.platform.curriculum.api;

/**
 * How binding a prerequisite is.
 *
 * <p>Two levels, and not a weight in [0,1]. ADR 0006 rejected the continuous weight because
 * there is no defensible origin for the number: human curation would produce something
 * arbitrary and irreproducible, and the optimisation core would gain one more parameter to
 * calibrate with no data justifying it.
 *
 * <p>What these two do have is a mechanism each in the core that already exists, so the
 * meaning is defined by the behaviour of the algorithm rather than by a number somebody
 * chose. The enumeration admits further levels later, without a structural change, if data
 * ever justifies one.
 */
public enum EdgeStrength {

    /** A constraint. Violating it invalidates the solution and triggers the repair operators. */
    HARD,

    /** A penalty in the fitness function. Violation is allowed, at a cost. */
    SOFT
}
