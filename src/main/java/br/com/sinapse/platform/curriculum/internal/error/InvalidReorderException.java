package br.com.sinapse.platform.curriculum.internal.error;

import br.com.sinapse.platform.shared.web.problem.ApiErrorType;
import br.com.sinapse.platform.shared.web.problem.ApiException;

/**
 * Raised when a reorder does not describe a complete, unambiguous order of a subject.
 *
 * <p>A reorder rewrites every position at once, so it has to name every topic of the subject
 * exactly once and give each a distinct position. A partial reorder would be indistinguishable
 * from a complete one that lost a row on the way, and the deferred unique constraint would
 * catch it at commit with a message about an index rather than about the request.
 */
public class InvalidReorderException extends ApiException {

    /** Creates the failure. */
    public InvalidReorderException() {
        super(ApiErrorType.CONFLICT);
    }
}
