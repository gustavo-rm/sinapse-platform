/**
 * Composition layer of plan generation: reads curriculum, learning record and
 * planning, assembles the snapshot, calls the Sinapse Core and stores the
 * resulting plan.
 *
 * <p>It exists as a component of its own precisely so that {@code planning}
 * never reads {@code learningrecord} directly (architecture rule R2); the
 * dependency graph stays acyclic because the composition happens here.
 */
@org.springframework.modulith.NamedInterface("orchestration")
package br.com.sinapse.platform.planning.orchestration;
