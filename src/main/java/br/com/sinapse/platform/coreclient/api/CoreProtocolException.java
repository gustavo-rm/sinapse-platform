package br.com.sinapse.platform.coreclient.api;

/**
 * The core answered with something this contract cannot read.
 *
 * <p>A body that is not JSON, a document missing the schedule, a session with no topic or a
 * negative duration. It is a different failure from being unavailable and is treated as one:
 * retrying a core that is answering wrongly produces the same wrong answer, and a partial
 * plan must never reach the database.
 */
public class CoreProtocolException extends CoreException {

    /**
     * @param message what was wrong with the answer, for the log
     */
    public CoreProtocolException(String message) {
        super(message);
    }

    /**
     * @param message what was wrong with the answer, for the log
     * @param cause   underlying failure
     */
    public CoreProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
