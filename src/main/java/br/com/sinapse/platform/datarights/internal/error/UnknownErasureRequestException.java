package br.com.sinapse.platform.datarights.internal.error;

import br.com.sinapse.platform.shared.web.problem.ApiErrorType;
import br.com.sinapse.platform.shared.web.problem.ApiException;

/**
 * Raised when the request does not exist, or belongs to somebody else.
 *
 * <p>The two answer identically. Separating them would let anyone holding an identifier learn
 * whether it names a real request, which is a fact about another person's account.
 */
public class UnknownErasureRequestException extends ApiException {

    /** Creates the failure. */
    public UnknownErasureRequestException() {
        super(ApiErrorType.RESOURCE_NOT_FOUND);
    }
}
