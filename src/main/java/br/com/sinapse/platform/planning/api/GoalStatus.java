package br.com.sinapse.platform.planning.api;

/**
 * Where a goal stands.
 *
 * <p>A goal is never deleted. It is achieved or it is abandoned, and both are recorded,
 * because a goal that was dropped halfway through the pilot is data about what students
 * actually pursue rather than clutter to be tidied away.
 */
public enum GoalStatus {

    /** The student is working towards it. At most one per subject, by partial index. */
    ACTIVE,

    /** The student says they are done with the subject. */
    ACHIEVED,

    /** The student stopped pursuing it without finishing. */
    ABANDONED;

    /** Whether the goal still enters plan generation. */
    public boolean isOpen() {
        return this == ACTIVE;
    }
}
