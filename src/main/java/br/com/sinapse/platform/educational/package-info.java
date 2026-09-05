/**
 * Teacher, classroom, invite and enrollment. The single source of a teacher's access to a student's data.
 *
 * <p>Application module root. Only the {@code api} sub-package is part of the
 * published surface; {@code internal} is off limits to every other module and
 * the build fails when that is violated (see {@code ModularityTests}).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Educational")
package br.com.sinapse.platform.educational;
