package br.com.sinapse.platform.planning.internal.error;

import br.com.sinapse.platform.shared.web.problem.ApiErrorType;
import br.com.sinapse.platform.shared.web.problem.ApiException;

/**
 * Raised when the goal does not exist, or belongs to somebody else.
 */
public class UnknownGoalException extends ApiException {

    /** Creates the failure. */
    public UnknownGoalException() {
        super(ApiErrorType.RESOURCE_NOT_FOUND);
    }
}
