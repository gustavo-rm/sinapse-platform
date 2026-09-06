package br.com.sinapse.platform.educational.internal.error;

import br.com.sinapse.platform.shared.web.problem.ApiErrorType;
import br.com.sinapse.platform.shared.web.problem.ApiException;

/**
 * Raised when a redemption is attempted by an account that may not share its data with an
 * institution — either it is not active, or the {@code INSTITUTION_SHARING} consent is not
 * in force.
 *
 * <p>The two are one question here, asked of {@code AccountAccessPolicy}. This module never
 * reads an identity table and never learns what a consent record is.
 */
public class SharingConsentRequiredException extends ApiException {

    /** Creates the failure. */
    public SharingConsentRequiredException() {
        super(ApiErrorType.ACCESS_DENIED);
    }
}
