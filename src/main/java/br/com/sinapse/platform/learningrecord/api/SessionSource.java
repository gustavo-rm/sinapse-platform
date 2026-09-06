package br.com.sinapse.platform.learningrecord.api;

/**
 * Whether the session came from a plan or the student simply studied.
 *
 * <p>This is the <em>only</em> difference between the two, alongside the presence of a
 * planned session identifier. Section 8.1 of the architecture document is explicit: a session
 * outside the plan is structurally identical to a planned one. Two shapes of record would
 * make the off-plan session second-class evidence, and it is the same evidence.
 */
public enum SessionSource {

    /** Executed against a session the plan scheduled. */
    FROM_PLAN,

    /** Studied on the student's own initiative. */
    SELF_DIRECTED
}
