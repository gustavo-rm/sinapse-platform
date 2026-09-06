package br.com.sinapse.platform.identity.internal.error;

import br.com.sinapse.platform.shared.web.problem.ApiErrorType;
import br.com.sinapse.platform.shared.web.problem.ApiException;

/**
 * Raised when the address given at registration already belongs to an account that has not
 * been anonymised.
 *
 * <p>Invariant 6. The address of an erased account is deliberately free again, so this is
 * about the accounts still in use and not about every row that ever existed.
 *
 * <p>It does disclose that an address is registered here. That is unavoidable at
 * registration — the alternative is to accept the request and let the holder discover the
 * problem never, which is worse — and it is the reason the login route and the password
 * reset route answer the same way whether the address exists or not.
 */
public class EmailAlreadyRegisteredException extends ApiException {

    /** Creates the failure. */
    public EmailAlreadyRegisteredException() {
        super(ApiErrorType.CONFLICT);
    }
}
