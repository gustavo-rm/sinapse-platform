package br.com.sinapse.platform.planning.internal.error;

import br.com.sinapse.platform.shared.web.problem.ApiErrorType;
import br.com.sinapse.platform.shared.web.problem.ApiException;

/**
 * Raised when a declared window would overlap one the student already has.
 *
 * <p>Same weekday, validity ranges that meet, times that meet. Two windows the student is
 * simultaneously available in would be counted twice by whatever allocates study time, and
 * the plan would be built on hours that do not exist.
 *
 * <p>There is no exclusion constraint on the table, so this is enforced in the application.
 * The service serialises the check per account, because a check made in application code
 * under read committed passes in two concurrent transactions at once.
 */
public class OverlappingAvailabilityException extends ApiException {

    /** Creates the failure. */
    public OverlappingAvailabilityException() {
        super(ApiErrorType.CONFLICT);
    }
}
