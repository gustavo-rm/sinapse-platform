package br.com.sinapse.platform.identity.internal.error;

import br.com.sinapse.platform.shared.web.problem.ApiErrorType;
import br.com.sinapse.platform.shared.web.problem.ApiException;

/**
 * Raised when authentication fails.
 *
 * <p>An unknown address and a wrong password raise the same failure, because separating
 * them would turn the login route into a way of finding out who has an account here.
 */
public class InvalidCredentialsException extends ApiException {

    /** Creates the failure. */
    public InvalidCredentialsException() {
        super(ApiErrorType.UNAUTHENTICATED);
    }
}
