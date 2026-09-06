package br.com.sinapse.platform.identity.internal.error;

import br.com.sinapse.platform.shared.web.problem.ApiErrorType;
import br.com.sinapse.platform.shared.web.problem.ApiException;

/**
 * Raised when an account would become active without a valid consent for every essential
 * purpose, or with one whose grantor does not match the holder's age at the time.
 *
 * <p>Invariant 1, the one that crosses two aggregates.
 */
public class EssentialConsentMissingException extends ApiException {

    /** Creates the failure. */
    public EssentialConsentMissingException() {
        super(ApiErrorType.CONFLICT);
    }
}
