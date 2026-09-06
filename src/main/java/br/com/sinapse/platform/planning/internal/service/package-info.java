/**
 * Use cases of the planning module: availability, goals, and the storage and reading of
 * generated plans.
 *
 * <p>Nothing here generates a plan. The job, the snapshot and the core client are the next
 * step of the build; what exists is the place a produced plan is stored, which is what makes
 * supersession a rule of this module rather than of whatever calls it.
 */
package br.com.sinapse.platform.planning.internal.service;
