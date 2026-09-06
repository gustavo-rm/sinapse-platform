package br.com.sinapse.platform.learningrecord.api;

/**
 * Where the duration of a session came from.
 *
 * <p>Required whenever there is a duration at all. Decision F5 of ADR 0012 makes the timer
 * the main path and retroactive entry a marked exception, and the marking is this field.
 *
 * <p>The reason is not tidiness. Duration is the most basic evidence this context holds, and
 * the v1 already has no objective measure of retention (ADR 0008). Accepting a self-reported
 * duration without distinguishing it would leave the pilot with no reliable measure at all;
 * distinguishing it allows the two sets to be analysed separately, and the second to be
 * discarded if it has to be.
 */
public enum DurationSource {

    /** The application timed the session while it ran. */
    MEASURED,

    /** The student entered the duration afterwards. */
    SELF_REPORTED
}
