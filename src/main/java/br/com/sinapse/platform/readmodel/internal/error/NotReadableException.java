package br.com.sinapse.platform.readmodel.internal.error;

import br.com.sinapse.platform.shared.web.problem.ApiErrorType;
import br.com.sinapse.platform.shared.web.problem.ApiException;

/**
 * Raised when there is nothing here for this caller to read.
 *
 * <p>One failure for four different situations — the classroom does not exist, it belongs to
 * another teacher, the student is not in it, or the student has withdrawn the consent that
 * made them visible — and that is the point. Distinguishing them would turn these routes into a
 * way of discovering which identifiers are real and which students have withdrawn, and the
 * second is itself information the consent governs.
 *
 * <p>Not found rather than forbidden, for the same reason. A teacher whose student has just
 * revoked sees the student disappear from the class list and then finds nothing at their
 * panel, which is consistent; a 403 would announce that there is something there being kept
 * from them.
 */
public class NotReadableException extends ApiException {

    /** Creates the failure. */
    public NotReadableException() {
        super(ApiErrorType.RESOURCE_NOT_FOUND);
    }
}
