package br.com.sinapse.platform.curriculum.internal.error;

import br.com.sinapse.platform.shared.web.problem.ApiErrorType;
import br.com.sinapse.platform.shared.web.problem.ApiException;

/**
 * Raised when an operation names a topic, a subject or an edge that is not in the catalogue.
 */
public class UnknownTopicException extends ApiException {

    /** Creates the failure. */
    public UnknownTopicException() {
        super(ApiErrorType.RESOURCE_NOT_FOUND);
    }
}
