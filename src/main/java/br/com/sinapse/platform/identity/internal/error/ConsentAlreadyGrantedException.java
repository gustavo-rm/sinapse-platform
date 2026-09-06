package br.com.sinapse.platform.identity.internal.error;

import br.com.sinapse.platform.shared.web.problem.ApiErrorType;
import br.com.sinapse.platform.shared.web.problem.ApiException;

/**
 * Raised when a purpose that already has a valid consent is consented to again.
 *
 * <p>The partial unique index makes this a guarantee of the database rather than a
 * discipline of the code; this exception is what turns it into an answer the client can
 * read.
 */
public class ConsentAlreadyGrantedException extends ApiException {

    /** Creates the failure. */
    public ConsentAlreadyGrantedException() {
        super(ApiErrorType.CONFLICT);
    }
}
