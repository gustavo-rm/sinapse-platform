package br.com.sinapse.platform.shared.security;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;

/**
 * Contribution of a module to the security chain of the public API.
 *
 * <p>A module that owns an authentication mechanism, or routes that are not open, needs
 * to add a filter and to declare which of its routes require a credential. Doing that
 * from the chain itself would force {@code shared} to import the module, which is the one
 * direction the dependency rules forbid. So the chain collects every implementation of
 * this interface and applies it before deciding what to do with the routes nobody
 * claimed.
 *
 * <p>Two consequences follow from the moment implementations are applied, and both are
 * deliberate:
 *
 * <ul>
 *   <li>authorisation rules registered here are evaluated <em>before</em> the fallback
 *       the chain adds for every remaining route, which is what makes the fallback a
 *       fallback rather than a blanket;</li>
 *   <li>a filter added here is anchored on a framework filter, never on another module's
 *       filter, so that two modules can never end up depending on the order in which
 *       Spring happened to instantiate them.</li>
 * </ul>
 */
@FunctionalInterface
public interface ApiSecurityCustomizer {

    /**
     * Applies this module's contribution to the chain under construction.
     *
     * @param http builder of the API chain
     * @throws Exception if the contribution cannot be applied, which fails startup rather
     *                   than leaving a route unprotected
     */
    void customize(HttpSecurity http) throws Exception;
}
