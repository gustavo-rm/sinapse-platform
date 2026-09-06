/**
 * Spring Data repositories of the identity aggregates.
 *
 * <p>Every method here is a query or a write of one aggregate. Nothing composes across
 * aggregates: composition belongs to the services, where the transaction is.
 */
package br.com.sinapse.platform.identity.internal.persistence;
