/**
 * The seam through which a module contributes to the security chain of the API.
 *
 * <p>The chain itself is assembled in {@code shared.config}, which must not know that
 * identity, or any other module, exists. A module that owns an authentication mechanism
 * or a set of protected routes publishes an
 * {@link br.com.sinapse.platform.shared.security.ApiSecurityCustomizer} instead, and the
 * dependency points from the module towards {@code shared}, which is the only direction
 * section 3 of the architecture allows.
 */
@org.springframework.modulith.NamedInterface("security")
package br.com.sinapse.platform.shared.security;
