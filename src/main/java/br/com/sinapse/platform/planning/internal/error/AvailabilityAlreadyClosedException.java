package br.com.sinapse.platform.planning.internal.error;

import br.com.sinapse.platform.shared.web.problem.ApiErrorType;
import br.com.sinapse.platform.shared.web.problem.ApiException;

/**
 * Raised on an attempt to close an availability window that has already been closed.
 *
 * <p>Moving the date would rewrite when the student's routine actually changed, and the
 * whole reason a window is closed instead of edited is that a past plan was generated
 * against what it said while it applied.
 */
public class AvailabilityAlreadyClosedException extends ApiException {

    /** Creates the failure. */
    public AvailabilityAlreadyClosedException() {
        super(ApiErrorType.CONFLICT);
    }
}
