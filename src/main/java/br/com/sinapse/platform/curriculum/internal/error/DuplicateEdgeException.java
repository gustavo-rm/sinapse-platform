package br.com.sinapse.platform.curriculum.internal.error;

import br.com.sinapse.platform.shared.web.problem.ApiErrorType;
import br.com.sinapse.platform.shared.web.problem.ApiException;

/**
 * Raised when an edge already exists for the same ordered pair of topics.
 *
 * <p>The pair is unique in the database. Asserting the same prerequisite twice is not an
 * error a curator needs a second row for; correcting the existing edge is what they meant.
 */
public class DuplicateEdgeException extends ApiException {

    /**
     * @param cause the database refusal, kept for the log and never serialised
     */
    public DuplicateEdgeException(Throwable cause) {
        super(ApiErrorType.CONFLICT, cause);
    }
}
