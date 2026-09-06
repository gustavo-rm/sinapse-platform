package br.com.sinapse.platform.curriculum.api;

import br.com.sinapse.platform.shared.web.problem.ApiErrorType;
import br.com.sinapse.platform.shared.web.problem.ApiException;

/**
 * Raised when an operation would leave the prerequisite graph cyclic, or when an order is
 * asked of a set of topics that already is.
 *
 * <p>The database refuses to store a cycle, with a trigger and a recursive query, and that
 * refusal arrives as a PostgreSQL error naming two identifiers. This is where it stops.
 * A caller gets a failure that says what rule was broken; the identifiers stay in the log,
 * because the shared catalogue is the only thing that may write a response body.
 *
 * <p>A cycle is not a matter of taste. Topological ordering only exists on an acyclic graph,
 * and a cycle reaching the optimisation core produces undefined behaviour — a hang, a loop,
 * or an arbitrary order depending on the implementation. It is a silent defect and an
 * expensive one to diagnose, which is why it is refused at the deepest level available
 * rather than in application code.
 */
public class PrerequisiteCycleException extends ApiException {

    /**
     * @param cause the database refusal, kept for the log and never serialised
     */
    public PrerequisiteCycleException(Throwable cause) {
        super(ApiErrorType.PREREQUISITE_CYCLE, cause);
    }

    /** Creates the failure with no underlying cause, for a cycle found in memory. */
    public PrerequisiteCycleException() {
        super(ApiErrorType.PREREQUISITE_CYCLE);
    }
}
