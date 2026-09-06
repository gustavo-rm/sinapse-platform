package br.com.sinapse.platform.coreclient.api;

/**
 * Base of the failures the core adapter raises.
 *
 * <p>Deliberately not an {@code ApiException}. No client request ever waits on the core —
 * generation is a job — so a core failure is never an HTTP response. It is something the job
 * records, and turning it into a status code here would invent a caller that does not exist.
 *
 * <p>The message is for the log and never for a response body. It names what happened and, at
 * most, a status code; the address of the core, the body it returned and the payload that was
 * sent stay out of it.
 */
public abstract class CoreException extends RuntimeException {

    /**
     * @param message what happened, for the log
     */
    protected CoreException(String message) {
        super(message);
    }

    /**
     * @param message what happened, for the log
     * @param cause   underlying failure
     */
    protected CoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
