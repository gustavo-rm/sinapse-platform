package br.com.sinapse.platform.learningrecord.internal.error;

import br.com.sinapse.platform.shared.web.problem.ApiErrorType;
import br.com.sinapse.platform.shared.web.problem.ApiException;

/**
 * Raised when a history is asked for over a window the server will not answer.
 *
 * <p>Section 1 of the API contract: a history grows without limit, so the window is mandatory
 * and its span is capped. There is no generic pagination to fall back on, by decision — a
 * cursor is a cheap addition once a concrete case asks for one, and building it now would be
 * speculative.
 */
public class InvalidTimeWindowException extends ApiException {

    /** Creates the failure. */
    public InvalidTimeWindowException() {
        super(ApiErrorType.TIME_WINDOW_INVALID);
    }
}
