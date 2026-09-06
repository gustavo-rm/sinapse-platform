package br.com.sinapse.platform.coreclient.api;

/**
 * The core could not be reached, refused the call, or did not answer in time.
 *
 * <p>The three are one failure from this side: nothing was produced, and trying again later
 * is the sensible response. That is why they share a type — the job's retry policy has no use
 * for the distinction, and the operator finds it in the log.
 */
public class CoreUnavailableException extends CoreException {

    /**
     * @param message what happened, for the log
     */
    public CoreUnavailableException(String message) {
        super(message);
    }

    /**
     * @param message what happened, for the log
     * @param cause   underlying failure
     */
    public CoreUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
