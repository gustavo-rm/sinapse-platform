package br.com.sinapse.platform.identity.internal.error;

import br.com.sinapse.platform.shared.web.problem.ApiErrorType;
import br.com.sinapse.platform.shared.web.problem.ApiException;

/**
 * Raised when the consent being acted upon does not exist, or is not valid, for the
 * account in question.
 */
public class ConsentNotFoundException extends ApiException {

    /** Creates the failure. */
    public ConsentNotFoundException() {
        super(ApiErrorType.RESOURCE_NOT_FOUND);
    }
}
