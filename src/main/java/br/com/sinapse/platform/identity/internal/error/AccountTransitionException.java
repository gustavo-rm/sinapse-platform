package br.com.sinapse.platform.identity.internal.error;

import br.com.sinapse.platform.shared.web.problem.ApiErrorType;
import br.com.sinapse.platform.shared.web.problem.ApiException;

/**
 * Raised when a transition is asked of an account that cannot make it — in practice, of
 * an anonymised one, whose state is terminal.
 */
public class AccountTransitionException extends ApiException {

    /** Creates the failure. */
    public AccountTransitionException() {
        super(ApiErrorType.CONFLICT);
    }
}
