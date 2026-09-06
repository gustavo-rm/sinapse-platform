package br.com.sinapse.platform.educational.internal.error;

import br.com.sinapse.platform.shared.web.problem.ApiErrorType;
import br.com.sinapse.platform.shared.web.problem.ApiException;

/**
 * Raised when the caller already has an active enrollment in the classroom.
 *
 * <p>The partial unique index makes this a guarantee of the database; the exception is what
 * turns it into an answer a client can read rather than a constraint violation.
 */
public class AlreadyEnrolledException extends ApiException {

    /** Creates the failure. */
    public AlreadyEnrolledException() {
        super(ApiErrorType.CONFLICT);
    }
}
