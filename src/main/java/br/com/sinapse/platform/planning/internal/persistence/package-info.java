/**
 * Spring Data repositories of the planning aggregates.
 *
 * <p>Nothing here deletes a plan or a planned session. A trigger refuses it, so a method
 * offering the operation would only be a way of turning an invariant into a runtime error.
 */
package br.com.sinapse.platform.planning.internal.persistence;
