package br.com.sinapse.platform.identity.internal.security;

import br.com.sinapse.platform.identity.internal.config.IdentityProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import org.springframework.http.ResponseCookie;

/**
 * The cookie that carries a session to a browser.
 *
 * <p>Four attributes, and each one is a decision:
 *
 * <ul>
 *   <li>{@code HttpOnly}, so that a script cannot read the token;</li>
 *   <li>{@code Secure}, so that it is never sent in clear;</li>
 *   <li>{@code SameSite=Strict}, which is the correction ADR 0010 makes to the premise
 *       prompt 00 disabled CSRF on. That premise — the API is only ever called with a header
 *       token — stopped being true the moment a browser cookie was chosen: a cookie is an
 *       ambient credential, and {@code Lax} still travels on a cross-site top-level
 *       navigation. With {@code Strict} the cookie does not leave the site at all, which is
 *       what stands in for a CSRF token here;</li>
 *   <li>a path scoped to the API, because nothing else on the origin has any use for it.</li>
 * </ul>
 *
 * <p>The remaining condition is that no state-changing operation is reachable by GET. That
 * is not left to discipline — a test walks every GET handler and fails the build if one of
 * them can reach a write.
 *
 * <p>E-mail verification and password reset links are unaffected by {@code Strict}: they
 * carry a token of their own and never depend on the session cookie.
 */
public final class SessionCookies {

    /** Value that ends a cookie rather than setting one. */
    private static final Duration EXPIRE_NOW = Duration.ZERO;

    private SessionCookies() {
    }

    /**
     * Builds the cookie that carries a freshly opened session.
     *
     * @param session  configured name, path and flags
     * @param token    clear token
     * @param lifetime how long the browser should keep it, normally the absolute expiry of
     *                 the session
     * @return the cookie
     */
    public static ResponseCookie issue(IdentityProperties.Session session, String token, Duration lifetime) {
        return base(session, token).maxAge(lifetime).build();
    }

    /**
     * Builds the cookie that ends a session on the client.
     *
     * @param session configured name, path and flags
     * @return an empty cookie that expires immediately
     */
    public static ResponseCookie clear(IdentityProperties.Session session) {
        return base(session, "").maxAge(EXPIRE_NOW).build();
    }

    /**
     * Reads the session cookie of a request.
     *
     * @param request current request
     * @param name    configured cookie name
     * @return the token carried, or {@code null}
     */
    public static String read(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private static ResponseCookie.ResponseCookieBuilder base(IdentityProperties.Session session, String value) {
        return ResponseCookie.from(session.cookieName(), value)
                .httpOnly(true)
                .secure(session.cookieSecure())
                .sameSite("Strict")
                .path(session.cookiePath());
    }
}
