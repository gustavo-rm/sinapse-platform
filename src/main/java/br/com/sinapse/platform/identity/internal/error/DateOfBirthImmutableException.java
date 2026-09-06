package br.com.sinapse.platform.identity.internal.error;

import br.com.sinapse.platform.shared.web.problem.ApiErrorType;
import br.com.sinapse.platform.shared.web.problem.ApiException;

/**
 * Raised when the date of birth of an account that has been active is changed.
 *
 * <p>Invariant 2. Before activation the field is a correction; after it, it is the route a
 * minor would take through the gate.
 */
public class DateOfBirthImmutableException extends ApiException {

    /** Creates the failure. */
    public DateOfBirthImmutableException() {
        super(ApiErrorType.CONFLICT);
    }
}
