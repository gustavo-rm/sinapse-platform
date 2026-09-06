package br.com.sinapse.platform.identity.internal.error;

import br.com.sinapse.platform.shared.web.problem.ApiErrorType;
import br.com.sinapse.platform.shared.web.problem.ApiException;

/**
 * Raised when a single-use token is unknown, expired, already spent, or presented for a
 * purpose other than the one it was issued for.
 */
public class InvalidTokenException extends ApiException {

    /** Creates the failure. */
    public InvalidTokenException() {
        super(ApiErrorType.RESOURCE_NOT_FOUND);
    }
}
