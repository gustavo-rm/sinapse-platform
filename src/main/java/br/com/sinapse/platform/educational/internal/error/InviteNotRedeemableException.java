package br.com.sinapse.platform.educational.internal.error;

import br.com.sinapse.platform.shared.web.problem.ApiErrorType;
import br.com.sinapse.platform.shared.web.problem.ApiException;

/**
 * Raised when a code does not admit anyone to anything: unknown, expired, revoked,
 * exhausted, or belonging to a classroom that has been archived.
 *
 * <p>All five answer identically, and that is the point. A code is fifty bits of entropy
 * stored in clear; an answer that distinguished "wrong code" from "this code is real but
 * expired" would hand a guesser exactly the signal the entropy exists to deny them.
 */
public class InviteNotRedeemableException extends ApiException {

    /** Creates the failure. */
    public InviteNotRedeemableException() {
        super(ApiErrorType.RESOURCE_NOT_FOUND);
    }
}
