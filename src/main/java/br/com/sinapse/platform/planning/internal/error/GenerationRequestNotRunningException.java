package br.com.sinapse.platform.planning.internal.error;

import br.com.sinapse.platform.shared.web.problem.ApiErrorType;
import br.com.sinapse.platform.shared.web.problem.ApiException;

/**
 * Raised when a job is moved as though a worker held it and none does.
 *
 * <p>Not reachable from any route: it guards the job's own state machine against a second
 * worker finishing a job the first is still running, which the claim is supposed to make
 * impossible. It exists so that the failure is a refusal rather than a silently rewritten
 * record.
 */
public class GenerationRequestNotRunningException extends ApiException {

    /** Creates the failure. */
    public GenerationRequestNotRunningException() {
        super(ApiErrorType.CONFLICT);
    }
}
