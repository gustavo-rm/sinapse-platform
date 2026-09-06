package br.com.sinapse.platform.learningrecord.internal.error;

import br.com.sinapse.platform.shared.web.problem.ApiErrorType;
import br.com.sinapse.platform.shared.web.problem.ApiException;

/**
 * Raised on an attempt to change a session that has already been completed or abandoned.
 *
 * <p>Invariant 2. A trigger refuses the update underneath this, so the exception is not what
 * protects the record — it is what tells the caller that the correction they meant to make is
 * a new session, not an edit of this one.
 */
public class SessionAlreadyClosedException extends ApiException {

    /** Creates the failure. */
    public SessionAlreadyClosedException() {
        super(ApiErrorType.CONFLICT);
    }
}
