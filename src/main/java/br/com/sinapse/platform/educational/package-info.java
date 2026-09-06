/**
 * Teacher, classroom, invite and enrollment. The single source of a teacher's access to a student's data.
 *
 * <p>Application module root. Only the {@code api} sub-package is part of the
 * published surface; {@code internal} is off limits to every other module and
 * the build fails when that is violated (see {@code ModularityTests}).
 *
 * <p><strong>Where this module sits.</strong> It depends on the base layer and on nothing
 * else, and the declaration below turns that into something the build checks rather than
 * something the diagram says. Planning and the learning record are downstream of this
 * module; an import of either would be the dependency graph acquiring the cycle section 3 of
 * the architecture document is built to avoid.
 *
 * <p>It reads {@code identity} to ask two questions and only those two: who is calling, and
 * whether that student's data may be shared with an institution right now. It never reads an
 * identity table and never learns what a consent record is.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Educational",
        allowedDependencies = {
                "identity :: api", "curriculum :: api",
                "shared :: web", "shared :: problem", "shared :: security",
                "shared :: datarights"})
package br.com.sinapse.platform.educational;
