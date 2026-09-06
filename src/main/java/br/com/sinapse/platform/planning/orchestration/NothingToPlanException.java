package br.com.sinapse.platform.planning.orchestration;

/**
 * Raised when a job is claimed and there is no longer anything to plan.
 *
 * <p>Generation is refused unless the student has availability and at least one goal, so
 * reaching this means both were there when the job was queued and one of them had gone by the
 * time it was claimed — a goal abandoned, a window closed. It ends the job rather than
 * producing a plan of nothing.
 *
 * <p>Not an {@code ApiException}: no request is waiting on this, and the job records it.
 */
public class NothingToPlanException extends RuntimeException {

    /**
     * @param message what was missing, for the log
     */
    public NothingToPlanException(String message) {
        super(message);
    }
}
