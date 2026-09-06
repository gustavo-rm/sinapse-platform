package br.com.sinapse.platform.identity.internal.error;

import br.com.sinapse.platform.shared.web.problem.ApiErrorType;
import br.com.sinapse.platform.shared.web.problem.ApiException;

/**
 * Raised when consent is asked for in the wrong name for the holder's age.
 *
 * <p>Invariant 3, in both directions: a holder below the configured threshold cannot have a
 * consent recorded without a guardian, and a holder below it cannot reaffirm one in their
 * own name — consent is still the guardian's to give.
 *
 * <p>The invariant is about the record and not about the account. A holder who has since
 * reached the threshold keeps the guardian who answered for them as a minor, because that
 * row is the record of an act that happened; what changes is who the next consent comes
 * from.
 */
public class GuardianRequiredException extends ApiException {

    /** Creates the failure. */
    public GuardianRequiredException() {
        super(ApiErrorType.CONFLICT);
    }
}
