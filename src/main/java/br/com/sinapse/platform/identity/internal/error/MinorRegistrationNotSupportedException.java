package br.com.sinapse.platform.identity.internal.error;

import br.com.sinapse.platform.shared.web.problem.ApiErrorType;
import br.com.sinapse.platform.shared.web.problem.ApiException;

/**
 * Raised when someone below the configured age threshold registers.
 *
 * <p>ADR 0004 keeps the guardian branch for the next version, so this is a refusal with a
 * reason and not a failure. The reason is in the catalogue; the date of birth that
 * triggered it never leaves the request.
 */
public class MinorRegistrationNotSupportedException extends ApiException {

    /** Creates the failure. */
    public MinorRegistrationNotSupportedException() {
        super(ApiErrorType.MINOR_REGISTRATION_NOT_SUPPORTED);
    }
}
