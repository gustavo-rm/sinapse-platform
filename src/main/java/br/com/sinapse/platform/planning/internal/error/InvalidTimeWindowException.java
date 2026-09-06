package br.com.sinapse.platform.planning.internal.error;

import br.com.sinapse.platform.shared.web.problem.ApiErrorType;
import br.com.sinapse.platform.shared.web.problem.ApiException;

/**
 * Raised when a schedule is asked for over a window the server will not answer.
 *
 * <p>Section 1 of the API contract: a schedule read that is not bounded is a way of asking
 * the server for every plan the student has ever had, and there is no generic pagination in
 * this API to fall back on.
 */
public class InvalidTimeWindowException extends ApiException {

    /** Creates the failure. */
    public InvalidTimeWindowException() {
        super(ApiErrorType.TIME_WINDOW_INVALID);
    }
}
