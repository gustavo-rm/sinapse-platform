/**
 * The failures this module raises deliberately.
 *
 * <p>Each one carries an entry of the shared error catalogue and no message of its own.
 * That is what keeps ADR 0009 structural rather than conventional: an endpoint added later
 * has no way of composing a {@code detail} out of a value it was handed, because the text
 * it will produce is already written in the catalogue.
 *
 * <p>Several distinct failures deliberately share a catalogue entry. A token that never
 * existed, one that expired and one that was already spent all answer
 * {@code RESOURCE_NOT_FOUND}: telling them apart would tell whoever is guessing which
 * guess was closest.
 */
package br.com.sinapse.platform.identity.internal.error;
