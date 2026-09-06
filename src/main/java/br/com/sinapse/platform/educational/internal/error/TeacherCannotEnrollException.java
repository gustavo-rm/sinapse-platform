package br.com.sinapse.platform.educational.internal.error;

import br.com.sinapse.platform.shared.web.problem.ApiErrorType;
import br.com.sinapse.platform.shared.web.problem.ApiException;

/**
 * Raised when a teacher redeems an invite to a classroom they own.
 *
 * <p>Invariant 4, and the only one of the six that the database does not check. It spans two
 * aggregates — the enrollment and the classroom's owner — and its failure mode does not
 * corrupt a legal record, unlike consent. So it lives in application code, and the test that
 * proves it is the only thing standing behind it.
 */
public class TeacherCannotEnrollException extends ApiException {

    /** Creates the failure. */
    public TeacherCannotEnrollException() {
        super(ApiErrorType.CONFLICT);
    }
}
