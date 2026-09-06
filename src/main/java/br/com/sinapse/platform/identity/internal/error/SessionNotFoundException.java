package br.com.sinapse.platform.identity.internal.error;

import br.com.sinapse.platform.shared.web.problem.ApiErrorType;
import br.com.sinapse.platform.shared.web.problem.ApiException;

/**
 * Raised when the session being ended does not exist, or does not belong to the caller.
 *
 * <p>The two cases share an answer on purpose: distinguishing them would confirm the
 * existence of another holder's session.
 */
public class SessionNotFoundException extends ApiException {

    /** Creates the failure. */
    public SessionNotFoundException() {
        super(ApiErrorType.RESOURCE_NOT_FOUND);
    }
}
