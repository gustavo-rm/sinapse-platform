package br.com.sinapse.platform.planning.api;

/**
 * Where a plan generation job stands.
 *
 * <p>The table that holds these is also the queue (ADR 0007): workers claim rows with
 * {@code select ... for update skip locked}, and a broker would be infrastructure to operate,
 * monitor and debug without a problem at the pilot's scale that justifies it.
 *
 * <p>A partial index allows at most one non-terminal job per account. That is resource
 * control rather than interface polish: each run costs minutes of CPU, and an impatient
 * student would otherwise queue dozens.
 *
 * <p>Nothing in this version executes a job. The states are declared here because the request
 * is a persisted structure of this module and a client polls for them.
 */
public enum GenerationRequestStatus {

    /** Waiting to be claimed. */
    PENDING,

    /** Claimed by a worker and running. */
    RUNNING,

    /** Finished, and its plan exists. */
    READY,

    /** Finished without a plan. {@code failureReason} says what happened. */
    FAILED,

    /** Withdrawn before it produced anything. */
    CANCELLED;

    /** Whether the job has finished, whatever the outcome. */
    public boolean isTerminal() {
        return this == READY || this == FAILED || this == CANCELLED;
    }
}
