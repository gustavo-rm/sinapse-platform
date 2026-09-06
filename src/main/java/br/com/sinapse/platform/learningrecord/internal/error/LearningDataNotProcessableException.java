package br.com.sinapse.platform.learningrecord.internal.error;

import br.com.sinapse.platform.shared.web.problem.ApiErrorType;
import br.com.sinapse.platform.shared.web.problem.ApiException;

/**
 * Raised when the account's learning data may not be processed at all.
 *
 * <p>Section 5.5 of the architecture document: the answer comes from
 * {@code AccountAccessPolicy} and from nowhere else, so a suspended account or a withdrawn
 * essential consent stops study being recorded without this module holding an opinion about
 * either. The caller learns it may not proceed and nothing about why.
 */
public class LearningDataNotProcessableException extends ApiException {

    /** Creates the failure. */
    public LearningDataNotProcessableException() {
        super(ApiErrorType.ACCESS_DENIED);
    }
}
