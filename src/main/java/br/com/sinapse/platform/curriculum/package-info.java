/**
 * Subject, topic and the prerequisite graph between topics.
 *
 * <p>Application module root. Only the {@code api} sub-package is part of the
 * published surface; {@code internal} is off limits to every other module and
 * the build fails when that is violated (see {@code ModularityTests}).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Curriculum")
package br.com.sinapse.platform.curriculum;
