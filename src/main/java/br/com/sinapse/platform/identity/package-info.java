/**
 * Account, credentials, roles, date of birth, guardian and consent records. Knows nothing about educational concepts.
 *
 * <p>Application module root. Only the {@code api} sub-package is part of the
 * published surface; {@code internal} is off limits to every other module and
 * the build fails when that is violated (see {@code ModularityTests}).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Identity")
package br.com.sinapse.platform.identity;
