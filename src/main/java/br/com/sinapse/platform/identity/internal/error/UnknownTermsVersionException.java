package br.com.sinapse.platform.identity.internal.error;

import br.com.sinapse.platform.shared.web.problem.ApiErrorType;
import br.com.sinapse.platform.shared.web.problem.ApiException;

/**
 * Raised when the accepted wording is not the one currently in force for that purpose.
 *
 * <p>The client submits the identifier of the text it displayed. If a new version was
 * published in between, the holder consented to something that is no longer the terms, and
 * the act has to be repeated against what they can actually read.
 */
public class UnknownTermsVersionException extends ApiException {

    /** Creates the failure. */
    public UnknownTermsVersionException() {
        super(ApiErrorType.CONFLICT);
    }
}
