/**
 * The aggregates of the planning module.
 *
 * <p>Four roots: availability, goals, the generation job and the plan. {@code PlannedSession}
 * is not a root — it is an internal entity of {@code StudyPlan}, which is the opposite choice
 * from {@code Topic} in the curriculum and is deliberate: a planned session is never read
 * without its plan, and the plan is the unit of immutability.
 */
package br.com.sinapse.platform.planning.internal.domain;
