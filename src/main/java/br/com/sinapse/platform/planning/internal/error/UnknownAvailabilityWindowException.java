package br.com.sinapse.platform.planning.internal.error;

import br.com.sinapse.platform.shared.web.problem.ApiErrorType;
import br.com.sinapse.platform.shared.web.problem.ApiException;

/**
 * Raised when the window does not exist, or belongs to somebody else.
 *
 * <p>The two answer identically. Separating them would let anyone holding an identifier
 * learn whether it names a real window, which is a fact about another student's week.
 */
public class UnknownAvailabilityWindowException extends ApiException {

    /** Creates the failure. */
    public UnknownAvailabilityWindowException() {
        super(ApiErrorType.RESOURCE_NOT_FOUND);
    }
}
