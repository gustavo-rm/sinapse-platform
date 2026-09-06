package br.com.sinapse.platform.identity.api;

import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.AuthenticatedPrincipal;

/**
 * Who a request belongs to, once its token has been resolved.
 *
 * <p>Published rather than internal because "who is calling" is a question every module
 * with a protected route has to ask, and identity is the module that answers it. The
 * alternative — each module reaching into the security context and guessing at the shape of
 * what it finds — is how a principal type ends up parsed from a string in four places.
 *
 * <p>The name is the account identifier, which is what the rate limiter keys an
 * authenticated counter by. It is an internal identifier and not personal data — no
 * address, no name, nothing that identifies a person outside this database — which is what
 * makes it safe to appear in a counter key and in a log line.
 *
 * @param accountId holder the request acts as
 * @param sessionId session the request arrived on, so that "this session" can be ended
 * @param roles     roles held at the moment the request was authenticated
 */
public record AuthenticatedAccount(UUID accountId, UUID sessionId, Set<AccountRole> roles)
        implements AuthenticatedPrincipal {

    @Override
    public String getName() {
        return accountId.toString();
    }
}
