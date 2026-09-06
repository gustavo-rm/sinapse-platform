/**
 * Availability, goals, generation job, generated plan and planned sessions. Holds the core's output.
 *
 * <p>Application module root. Only the {@code api} sub-package is part of the
 * published surface; {@code internal} is off limits to every other module and
 * the build fails when that is violated (see {@code ModularityTests}).
 *
 * <p><strong>About the dependencies on {@code learningrecord} and {@code coreclient}.</strong>
 * Rule R2 says planning does not read the learning record, and section 9.5 says the optimiser
 * is reached from {@code planning.orchestration}. That sub-package is part of this module, so
 * the descriptor below has to permit both dependencies for the whole module — Spring Modulith
 * declares allowed targets per module and cannot say "only from this package". The narrower
 * rule, which is the one the architecture actually states, is enforced by
 * {@code OrchestrationIsTheOnlyComposerTest}: nothing in {@code api} or {@code internal} may
 * touch either of them.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Planning",
        allowedDependencies = {
                "identity :: api", "curriculum :: api", "learningrecord :: api",
                "coreclient :: api", "coreclient :: contract",
                "shared :: web", "shared :: problem", "shared :: security",
                "shared :: datarights"})
package br.com.sinapse.platform.planning;
