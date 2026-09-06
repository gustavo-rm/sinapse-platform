package br.com.sinapse.platform.datarights.internal.error;

import br.com.sinapse.platform.shared.web.problem.ApiErrorType;
import br.com.sinapse.platform.shared.web.problem.ApiException;

/**
 * Raised when a request is moved after its window has closed.
 *
 * <p>From a route, this is a holder trying to withdraw a request that has already been carried
 * out — and by then there is nothing to withdraw, because the data is gone.
 */
public class ErasureRequestNotOpenException extends ApiException {

    /** Creates the failure. */
    public ErasureRequestNotOpenException() {
        super(ApiErrorType.CONFLICT);
    }
}
