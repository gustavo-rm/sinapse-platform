/**
 * Subject, topic and the prerequisite graph between topics.
 *
 * <p>Application module root. Only the {@code api} sub-package is part of the
 * published surface; {@code internal} is off limits to every other module and
 * the build fails when that is violated (see {@code ModularityTests}).
 *
 * <p><strong>Base layer, verified rather than documented.</strong> Architecture rule R3 says
 * this module depends on no other context: it does not know what a student is. The
 * declaration below turns that into something the build checks. What it may reach for is
 * shared infrastructure, never another context: the error catalogue, because ADR 0009 makes
 * that contract mandatory for every module — a module that could not reach it would write
 * error bodies of its own, which is what the ADR exists to prevent — and, for its read-only
 * catalogue routes, the API prefix and the security chain's extension point, which is how a
 * module declares that its routes need a signed-in caller without depending on identity.
 *
 * <p>This is also why {@code topic_prerequisite.created_by} is a bare {@code uuid} with no
 * foreign key. It holds the account of the curator, and a foreign key to {@code account}
 * would point from the base layer towards identity — the one direction rule R4 forbids.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Curriculum",
        allowedDependencies = {"shared :: problem", "shared :: web", "shared :: security"})
package br.com.sinapse.platform.curriculum;
