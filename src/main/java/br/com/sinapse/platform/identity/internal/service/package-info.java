/**
 * Use cases of the identity context.
 *
 * <p>One of them is different from the others. Invariant 1 — an active account has a valid
 * consent for every essential purpose, granted by whoever the holder's age at the time
 * made appropriate — crosses two aggregates, and section 5.3 of the architecture document
 * settles how it is held: a single service writes both, inside one transaction, and the
 * logic is not distributed. That service is {@code ConsentService}, and
 * {@code ConsentServiceIsTheOnlyWriterTest} fails the build when a second writer appears.
 */
package br.com.sinapse.platform.identity.internal.service;
