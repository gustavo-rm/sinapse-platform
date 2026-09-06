package br.com.sinapse.platform.identity.internal.security;

import br.com.sinapse.platform.identity.api.AuthenticatedAccount;
import br.com.sinapse.platform.identity.internal.config.IdentityProperties;
import br.com.sinapse.platform.identity.internal.service.AuthenticationService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Attributes a request to an account, when it carries a usable session token.
 *
 * <p>An absent, unknown, expired or revoked token leaves the context empty and the request
 * continues as anonymous. It is not rejected here: whether anonymity is acceptable is a
 * property of the route, and the chain already answers that with the shared error contract.
 * Answering it here as well would give two different 401 bodies for the same situation.
 *
 * <p>The database is read on every authenticated request. ADR 0010 addressed the objection
 * directly: every read of a student's data already queries the database to consult the
 * access policy, so an indexed lookup by token hash is added to something that happens
 * anyway — and it is what makes a revocation take effect on the next request instead of at
 * the next expiry.
 */
public class SessionAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthenticationService authentication;
    private final IdentityProperties properties;

    /**
     * @param authentication resolver of a token into an account
     * @param properties     configured cookie name
     */
    public SessionAuthenticationFilter(AuthenticationService authentication, IdentityProperties properties) {
        this.authentication = authentication;
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String token = tokenOf(request);
        if (token != null) {
            authentication.resolve(token).ifPresent(SessionAuthenticationFilter::authenticate);
        }
        filterChain.doFilter(request, response);
    }

    private static void authenticate(AuthenticatedAccount account) {
        List<GrantedAuthority> authorities = account.roles().stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(role.authority()))
                .toList();

        AbstractAuthenticationToken token = new SessionAuthenticationToken(account, authorities);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(token);
        SecurityContextHolder.setContext(context);
    }

    /**
     * The token of a request, from the header first and the cookie second.
     *
     * <p>The order matters for a browser that is also scripted: an explicit header is a
     * deliberate credential, while the cookie is the ambient one.
     */
    private String tokenOf(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            String value = authorization.substring(BEARER_PREFIX.length()).trim();
            if (!value.isEmpty()) {
                return value;
            }
        }
        return SessionCookies.read(request, properties.session().cookieName());
    }

    /** The authentication a resolved session produces. Always already authenticated. */
    private static final class SessionAuthenticationToken extends AbstractAuthenticationToken {

        private final transient AuthenticatedAccount account;

        private SessionAuthenticationToken(AuthenticatedAccount account,
                List<GrantedAuthority> authorities) {
            super(authorities);
            this.account = account;
            setAuthenticated(true);
        }

        @Override
        public Object getCredentials() {
            // The credential was the token, and it is not kept: nothing downstream needs it,
            // and anything that holds it can log it.
            return null;
        }

        @Override
        public Object getPrincipal() {
            return account;
        }
    }
}
