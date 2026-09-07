package br.com.sinapse.platform.readmodel.internal.error;

import br.com.sinapse.platform.shared.web.problem.ApiErrorType;
import br.com.sinapse.platform.shared.web.problem.ApiException;

/**
 * Raised when the caller's own learning data may not be processed at all.
 *
 * <p>The answer comes from {@code AccountAccessPolicy} and from nowhere else, so a suspended
 * account or a withdrawn essential consent stops these reads without this package holding an
 * opinion about either.
 *
 * <p>Forbidden rather than not found, unlike {@link NotReadableException}: this is the caller
 * asking about themselves, so there is nothing to conceal — they already know the account
 * exists, and telling them plainly that it may not be read is what lets them go and fix it.
 */
public class LearningDataNotReadableException extends ApiException {

    /** Creates the failure. */
    public LearningDataNotReadableException() {
        super(ApiErrorType.ACCESS_DENIED);
    }
}
