package br.com.sinapse.platform.planning.internal.error;

import br.com.sinapse.platform.shared.web.problem.ApiErrorType;
import br.com.sinapse.platform.shared.web.problem.ApiException;

/**
 * Raised when the job does not exist, or belongs to somebody else.
 *
 * <p>The two answer identically. Separating them would let anyone holding an identifier learn
 * whether it names a real job, which is a fact about another student.
 */
public class UnknownGenerationRequestException extends ApiException {

    /** Creates the failure. */
    public UnknownGenerationRequestException() {
        super(ApiErrorType.RESOURCE_NOT_FOUND);
    }
}
