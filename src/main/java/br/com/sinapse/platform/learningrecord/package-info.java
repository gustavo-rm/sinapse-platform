/**
 * Study sessions actually executed, append-only. The source of learning evidence consumed by the core.
 *
 * <p>Application module root. Only the {@code api} sub-package is part of the
 * published surface; {@code internal} is off limits to every other module and
 * the build fails when that is violated (see {@code ModularityTests}).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Learning Record")
package br.com.sinapse.platform.learningrecord;
