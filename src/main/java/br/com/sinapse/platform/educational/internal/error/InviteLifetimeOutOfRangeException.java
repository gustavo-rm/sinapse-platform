package br.com.sinapse.platform.educational.internal.error;

import br.com.sinapse.platform.shared.web.problem.ApiErrorType;
import br.com.sinapse.platform.shared.web.problem.ApiException;

/**
 * Raised when an invite is asked to last for no time at all, or for longer than the configured
 * ceiling.
 *
 * <p>Expiry is mandatory on every invite, because a code in clear text that never expires is a
 * credential nobody remembers exists. A ceiling is what keeps that from being a formality a
 * client can set to a century.
 */
public class InviteLifetimeOutOfRangeException extends ApiException {

    /** Creates the failure. */
    public InviteLifetimeOutOfRangeException() {
        super(ApiErrorType.VALIDATION_FAILED);
    }
}
