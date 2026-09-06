package br.com.sinapse.platform.identity.internal.security;

import br.com.sinapse.platform.identity.internal.config.IdentityProperties;
import br.com.sinapse.platform.identity.internal.service.AuthenticationService;
import br.com.sinapse.platform.identity.internal.web.IdentityRoutes;
import br.com.sinapse.platform.shared.security.ApiSecurityCustomizer;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.stereotype.Component;

/**
 * What identity contributes to the security chain: the filter that resolves a session, and
 * the list of its own routes that require one.
 *
 * <p>The routes are enumerated rather than expressed as "everything except". An enumeration
 * of the open routes is auditable — six lines, each of which has to be argued for — while
 * "everything except" leaves a route added later open by default, and nobody notices until
 * it matters.
 *
 * <p>The filter is anchored on {@link AnonymousAuthenticationFilter}, a framework filter
 * that is always present, and never on another module's filter. That keeps it after the
 * rate limiter that guards anonymous routes and before the one that keys its counter by
 * account, which is the order ADR 0009 asks for, without any module having to know that the
 * other exists.
 */
@Component
public class IdentitySecurityCustomizer implements ApiSecurityCustomizer {

    private final AuthenticationService authentication;
    private final IdentityProperties properties;

    /**
     * @param authentication resolver of a session token
     * @param properties     configured cookie name
     */
    public IdentitySecurityCustomizer(AuthenticationService authentication, IdentityProperties properties) {
        this.authentication = authentication;
        this.properties = properties;
    }

    @Override
    public void customize(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(requests -> requests
                        // Open, and each for a reason: whoever calls them has no session yet,
                        // by definition of what they do.
                        .requestMatchers(HttpMethod.POST, IdentityRoutes.ACCOUNTS).permitAll()
                        .requestMatchers(HttpMethod.POST, IdentityRoutes.EMAIL_VERIFICATIONS).permitAll()
                        .requestMatchers(HttpMethod.POST, IdentityRoutes.SESSIONS).permitAll()
                        .requestMatchers(HttpMethod.POST, IdentityRoutes.PASSWORD_RESETS).permitAll()
                        .requestMatchers(HttpMethod.POST, IdentityRoutes.PASSWORD_RESET_CONFIRMATION).permitAll()
                        // Reachable from the message the majority sweep triggers, which the
                        // holder may open without signing in. The token in the body is the
                        // credential; a signed-in holder may call it without one.
                        .requestMatchers(HttpMethod.POST, IdentityRoutes.CONSENT_REAFFIRMATION).permitAll()
                        // The wordings have to be readable before there is anyone to read them.
                        .requestMatchers(HttpMethod.GET, IdentityRoutes.TERMS).permitAll()

                        .requestMatchers(IdentityRoutes.CONSENTS, IdentityRoutes.CONSENTS_ANY).authenticated()
                        .requestMatchers(IdentityRoutes.SESSIONS, IdentityRoutes.SESSIONS_ANY).authenticated()
                        .requestMatchers(IdentityRoutes.PASSWORD_CHANGES).authenticated())
                .addFilterBefore(new SessionAuthenticationFilter(authentication, properties),
                        AnonymousAuthenticationFilter.class);
    }
}
