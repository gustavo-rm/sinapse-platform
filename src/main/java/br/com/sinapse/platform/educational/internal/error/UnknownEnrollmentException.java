package br.com.sinapse.platform.educational.internal.error;

import br.com.sinapse.platform.shared.web.problem.ApiErrorType;
import br.com.sinapse.platform.shared.web.problem.ApiException;

/**
 * Raised when the enrollment named does not exist, or does not belong to the caller.
 */
public class UnknownEnrollmentException extends ApiException {

    /** Creates the failure. */
    public UnknownEnrollmentException() {
        super(ApiErrorType.RESOURCE_NOT_FOUND);
    }
}
