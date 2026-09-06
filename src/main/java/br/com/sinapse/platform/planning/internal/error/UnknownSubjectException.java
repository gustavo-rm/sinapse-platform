package br.com.sinapse.platform.planning.internal.error;

import br.com.sinapse.platform.shared.web.problem.ApiErrorType;
import br.com.sinapse.platform.shared.web.problem.ApiException;

/**
 * Raised when the subject a goal names is not in the catalogue.
 *
 * <p>The foreign key would refuse the row anyway. Asking the catalogue first is what turns
 * that into a 404 rather than a constraint violation the caller cannot interpret.
 */
public class UnknownSubjectException extends ApiException {

    /** Creates the failure. */
    public UnknownSubjectException() {
        super(ApiErrorType.RESOURCE_NOT_FOUND);
    }
}
