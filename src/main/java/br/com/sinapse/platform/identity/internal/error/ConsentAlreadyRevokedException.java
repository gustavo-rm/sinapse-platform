package br.com.sinapse.platform.identity.internal.error;

import br.com.sinapse.platform.shared.web.problem.ApiErrorType;
import br.com.sinapse.platform.shared.web.problem.ApiException;

/**
 * Raised when a consent record that is already revoked is revoked again.
 *
 * <p>Invariant 4 allows exactly one write of {@code revokedAt}; a second one would rewrite
 * a legal record.
 */
public class ConsentAlreadyRevokedException extends ApiException {

    /** Creates the failure. */
    public ConsentAlreadyRevokedException() {
        super(ApiErrorType.CONFLICT);
    }
}
