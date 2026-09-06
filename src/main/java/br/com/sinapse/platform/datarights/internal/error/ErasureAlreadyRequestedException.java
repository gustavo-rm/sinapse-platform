package br.com.sinapse.platform.datarights.internal.error;

import br.com.sinapse.platform.shared.web.problem.ApiErrorType;
import br.com.sinapse.platform.shared.web.problem.ApiException;

/**
 * Raised when the account already has an open erasure request.
 *
 * <p>The partial index makes it a guarantee of the database. A second request would not do
 * anything the first is not already doing, and it would move the seven-day window, which is the
 * protection the delay exists to give.
 */
public class ErasureAlreadyRequestedException extends ApiException {

    /** Creates the failure. */
    public ErasureAlreadyRequestedException() {
        super(ApiErrorType.CONFLICT);
    }
}
