package br.com.sinapse.platform.planning.internal.error;

import br.com.sinapse.platform.shared.web.problem.ApiErrorType;
import br.com.sinapse.platform.shared.web.problem.ApiException;

/**
 * Raised when the account's learning data may not be processed at all.
 *
 * <p>Section 5.5 of the architecture document: the answer comes from
 * {@code AccountAccessPolicy} and from nowhere else, so a suspended account or a withdrawn
 * essential consent stops planning without this module holding an opinion about either.
 */
public class PlanningDataNotProcessableException extends ApiException {

    /** Creates the failure. */
    public PlanningDataNotProcessableException() {
        super(ApiErrorType.ACCESS_DENIED);
    }
}
