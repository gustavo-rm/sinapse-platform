package br.com.sinapse.platform.planning.api;

/**
 * Why a generation job did not produce a plan.
 *
 * <p>A closed set, and the only thing stored in {@code failure_reason}. ADR 0009 forbids
 * internal structure from reaching a client, and a free-text reason on a job is a response body
 * by another route: it would carry the core's address, the status it returned, or a stack
 * frame. What a client can act on is which kind of failure it was, and that is all this says.
 * The particulars are in the log.
 */
public enum PlanGenerationFailure {

    /**
     * The core could not be reached, refused the connection, or did not answer in time.
     *
     * <p>The only failure worth another attempt: nothing about the request was wrong, so the
     * same request may work later.
     */
    CORE_UNAVAILABLE,

    /**
     * The core rejected the request, or answered with something the contract cannot read.
     *
     * <p>Not retried. The payload would be identical on the next attempt and so would the
     * answer; retrying would spend the budget establishing that.
     */
    CORE_REJECTED,

    /**
     * There was nothing to plan by the time the job ran.
     *
     * <p>Generation is refused unless the student has availability and at least one goal, so
     * this means both were there when the job was queued and one of them was gone when it was
     * claimed. Not retried: the student has to put it back.
     */
    NOTHING_TO_PLAN,

    /** Anything the backend itself failed at while running the job. */
    INTERNAL;

    /** Whether another attempt could plausibly do better. */
    public boolean isWorthRetrying() {
        return this == CORE_UNAVAILABLE;
    }
}
