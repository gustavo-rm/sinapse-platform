package br.com.sinapse.platform.learningrecord.internal.error;

import br.com.sinapse.platform.shared.web.problem.ApiErrorType;
import br.com.sinapse.platform.shared.web.problem.ApiException;

/**
 * Raised when the session does not exist, or belongs to somebody else.
 *
 * <p>The two answer identically. Separating them would let anyone with an identifier learn
 * whether it names a real study session, which is a fact about another student.
 */
public class UnknownSessionException extends ApiException {

    /** Creates the failure. */
    public UnknownSessionException() {
        super(ApiErrorType.RESOURCE_NOT_FOUND);
    }
}
