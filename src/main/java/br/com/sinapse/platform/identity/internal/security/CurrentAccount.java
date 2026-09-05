package br.com.sinapse.platform.identity.internal.security;

import br.com.sinapse.platform.shared.web.problem.ApiErrorType;
import br.com.sinapse.platform.shared.web.problem.ApiException;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Reads the account a request is acting as.
 *
 * <p>A single place, so that a controller never reaches into the security context and
 * guesses at the shape of what it finds there.
 */
public final class CurrentAccount {

    private CurrentAccount() {
    }

    /**
     * The account of the current request, if it is authenticated.
     *
     * @return the principal, or empty for an anonymous request
     */
    public static Optional<AuthenticatedAccount> find() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        return authentication.getPrincipal() instanceof AuthenticatedAccount account
                ? Optional.of(account)
                : Optional.empty();
    }

    /**
     * The account of the current request.
     *
     * @return the principal
     * @throws ApiException if the request is anonymous, which on a route that reaches this
     *                      means the route was left out of the authenticated list
     */
    public static AuthenticatedAccount require() {
        return find().orElseThrow(() -> new ApiException(ApiErrorType.UNAUTHENTICATED));
    }
}
