/**
 * Availability, goals, generation job, generated plan and planned sessions. Holds the core's output.
 *
 * <p>Application module root. Only the {@code api} sub-package is part of the
 * published surface; {@code internal} is off limits to every other module and
 * the build fails when that is violated (see {@code ModularityTests}).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Planning")
package br.com.sinapse.platform.planning;
