package br.com.sinapse.platform.planning.internal.error;

import br.com.sinapse.platform.shared.web.problem.ApiErrorType;
import br.com.sinapse.platform.shared.web.problem.ApiException;

/**
 * Raised when the account already has an active goal for the subject.
 *
 * <p>The partial index makes this a guarantee of the database; the exception is what turns
 * it into an answer a client can read. Two active goals for one subject would put the same
 * topics into the snapshot twice with two priorities, and the core would have no way to
 * decide which the student meant.
 */
public class DuplicateGoalException extends ApiException {

    /** Creates the failure. */
    public DuplicateGoalException() {
        super(ApiErrorType.CONFLICT);
    }
}
