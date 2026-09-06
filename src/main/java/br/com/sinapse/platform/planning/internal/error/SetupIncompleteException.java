package br.com.sinapse.platform.planning.internal.error;

import br.com.sinapse.platform.shared.web.problem.ApiErrorType;
import br.com.sinapse.platform.shared.web.problem.ApiException;

/**
 * Raised when a plan is asked for before there is anything to plan.
 *
 * <p>Decision F2 requires availability and at least one goal, and nothing beyond them. Without
 * the two the optimiser has nothing to optimise, and a plan assembled from defaults would be
 * fiction presented as a recommendation.
 */
public class SetupIncompleteException extends ApiException {

    /** Creates the failure. */
    public SetupIncompleteException() {
        super(ApiErrorType.SETUP_INCOMPLETE);
    }
}
