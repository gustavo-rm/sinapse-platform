/**
 * Account, credentials, roles, date of birth, guardian and consent records. Knows nothing about educational concepts.
 *
 * <p>Application module root. Only the {@code api} sub-package is part of the
 * published surface; {@code internal} is off limits to every other module and
 * the build fails when that is violated (see {@code ModularityTests}).
 *
 * <p><strong>Base layer, verified rather than documented.</strong> Architecture rule R3
 * says this module depends on no other context — it does not know what a subject, a
 * classroom or a plan is. The declaration below turns that into something the build checks:
 * the only things identity may reach for are the three published surfaces of {@code shared},
 * which are cross-cutting infrastructure and not a bounded context. Any import of another
 * module fails {@code ModularityTests}.
 *
 * <p>The list is what it is, rather than empty, because the error contract of ADR 0009 is
 * mandatory for every module: a module that could not reach it would have to write its own
 * error bodies, which is exactly what that ADR exists to prevent.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Identity",
        allowedDependencies = {"shared :: web", "shared :: problem", "shared :: security", "shared :: datarights"})
package br.com.sinapse.platform.identity;
