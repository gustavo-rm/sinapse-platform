package br.com.sinapse.platform.learningrecord.internal.error;

import br.com.sinapse.platform.shared.web.problem.ApiErrorType;
import br.com.sinapse.platform.shared.web.problem.ApiException;

/**
 * Raised when the topic being studied is not in the catalogue.
 *
 * <p>The foreign key would refuse the row anyway. Asking the catalogue first is what turns
 * that into a 404 instead of a constraint violation that the caller cannot interpret and that
 * would have to be logged with the identifier in it to be diagnosable.
 */
public class UnknownTopicException extends ApiException {

    /** Creates the failure. */
    public UnknownTopicException() {
        super(ApiErrorType.RESOURCE_NOT_FOUND);
    }
}
