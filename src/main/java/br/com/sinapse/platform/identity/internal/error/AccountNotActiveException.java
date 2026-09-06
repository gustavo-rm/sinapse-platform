package br.com.sinapse.platform.identity.internal.error;

import br.com.sinapse.platform.shared.web.problem.ApiErrorType;
import br.com.sinapse.platform.shared.web.problem.ApiException;

/**
 * Raised when an operation that requires a usable account is attempted on one that is
 * not active.
 */
public class AccountNotActiveException extends ApiException {

    /** Creates the failure. */
    public AccountNotActiveException() {
        super(ApiErrorType.ACCESS_DENIED);
    }
}
