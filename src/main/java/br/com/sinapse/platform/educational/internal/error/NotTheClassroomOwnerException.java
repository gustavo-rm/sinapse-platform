package br.com.sinapse.platform.educational.internal.error;

import br.com.sinapse.platform.shared.web.problem.ApiErrorType;
import br.com.sinapse.platform.shared.web.problem.ApiException;

/**
 * Raised when a teacher acts on a classroom that is not theirs.
 *
 * <p>Answers as "not found" rather than "forbidden", so that the route cannot be used to
 * discover which classroom identifiers exist.
 */
public class NotTheClassroomOwnerException extends ApiException {

    /** Creates the failure. */
    public NotTheClassroomOwnerException() {
        super(ApiErrorType.RESOURCE_NOT_FOUND);
    }
}
