package br.com.sinapse.platform.educational.internal.error;

import br.com.sinapse.platform.shared.web.problem.ApiErrorType;
import br.com.sinapse.platform.shared.web.problem.ApiException;

/**
 * Raised when an enrollment that has already ended is ended again.
 */
public class EnrollmentAlreadyEndedException extends ApiException {

    /** Creates the failure. */
    public EnrollmentAlreadyEndedException() {
        super(ApiErrorType.CONFLICT);
    }
}
