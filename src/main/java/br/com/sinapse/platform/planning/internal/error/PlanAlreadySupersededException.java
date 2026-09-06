package br.com.sinapse.platform.planning.internal.error;

import br.com.sinapse.platform.shared.web.problem.ApiErrorType;
import br.com.sinapse.platform.shared.web.problem.ApiException;

/**
 * Raised on an attempt to supersede a plan that has already been replaced.
 *
 * <p>The chain of superseded plans is experimental data about when a student re-plans;
 * moving the instant would corrupt it.
 */
public class PlanAlreadySupersededException extends ApiException {

    /** Creates the failure. */
    public PlanAlreadySupersededException() {
        super(ApiErrorType.CONFLICT);
    }
}
