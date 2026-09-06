package br.com.sinapse.platform.planning.internal.error;

import br.com.sinapse.platform.shared.web.problem.ApiErrorType;
import br.com.sinapse.platform.shared.web.problem.ApiException;

/**
 * Raised on an attempt to change a goal that has been achieved or abandoned.
 *
 * <p>A closed goal is a record of what the student pursued and stopped pursuing. Reopening
 * it by revising it would lose the instant they stopped, which is the more interesting half
 * of the data.
 */
public class GoalAlreadyClosedException extends ApiException {

    /** Creates the failure. */
    public GoalAlreadyClosedException() {
        super(ApiErrorType.CONFLICT);
    }
}
