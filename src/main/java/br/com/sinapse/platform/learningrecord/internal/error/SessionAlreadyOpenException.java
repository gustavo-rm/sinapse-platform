package br.com.sinapse.platform.learningrecord.internal.error;

import br.com.sinapse.platform.shared.web.problem.ApiErrorType;
import br.com.sinapse.platform.shared.web.problem.ApiException;

/**
 * Raised when the account already has a session in progress.
 *
 * <p>Invariant 1, and the partial unique index is what actually guarantees it. Two concurrent
 * sessions would corrupt the duration record, which is the most basic evidence this module
 * holds; the screen is expected to offer resuming the open session rather than reaching this.
 */
public class SessionAlreadyOpenException extends ApiException {

    /** Creates the failure. */
    public SessionAlreadyOpenException() {
        super(ApiErrorType.CONFLICT);
    }
}
