package br.com.sinapse.platform.planning.internal.error;

import br.com.sinapse.platform.shared.web.problem.ApiErrorType;
import br.com.sinapse.platform.shared.web.problem.ApiException;

/**
 * Raised when the account already has a job that has not finished.
 *
 * <p>The partial index makes it a guarantee of the database; this is what turns it into the
 * clear message the flow asks for rather than a constraint violation. Each run costs minutes
 * of CPU, so the limit is resource control and not interface polish.
 */
public class GenerationAlreadyRunningException extends ApiException {

    /** Creates the failure. */
    public GenerationAlreadyRunningException() {
        super(ApiErrorType.CONFLICT);
    }
}
