/**
 * Executed study sessions. The evidence side of the interaction with the optimisation core.
 *
 * <p>Application module root. Only the {@code api} sub-package is part of the
 * published surface; {@code internal} is off limits to every other module and
 * the build fails when that is violated (see {@code ModularityTests}).
 *
 * <p><strong>This module does not know what a plan is.</strong> Rule R2 forbids planning and
 * the learning record from depending on each other, and the declaration below turns that into
 * something the build checks. {@code plannedSessionId} is a bare identifier with no foreign
 * key for exactly this reason: keeping the evidence independent of the planning is what lets
 * a session recorded off-plan be structurally identical to one recorded from a plan. The
 * accepted cost is an orphan reference, which a consistency job reports and never deletes.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Learning Record",
        allowedDependencies = {
                "identity :: api", "curriculum :: api",
                "shared :: web", "shared :: problem", "shared :: security"})
package br.com.sinapse.platform.learningrecord;
