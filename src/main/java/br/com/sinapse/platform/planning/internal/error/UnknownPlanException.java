package br.com.sinapse.platform.planning.internal.error;

import br.com.sinapse.platform.shared.web.problem.ApiErrorType;
import br.com.sinapse.platform.shared.web.problem.ApiException;

/**
 * Raised when the plan does not exist, or belongs to somebody else.
 */
public class UnknownPlanException extends ApiException {

    /** Creates the failure. */
    public UnknownPlanException() {
        super(ApiErrorType.RESOURCE_NOT_FOUND);
    }
}
