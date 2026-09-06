/**
 * The failures the learning record module raises deliberately.
 *
 * <p>Each carries an entry of the shared catalogue and no message of its own, so that no
 * response can be assembled from a value the caller submitted.
 *
 * <p>Two of them mirror a constraint the database also enforces. That is not redundancy: the
 * database guarantees the invariant, and the exception is what turns the guarantee into an
 * answer a client can act on rather than a constraint violation surfacing as a 500.
 */
package br.com.sinapse.platform.learningrecord.internal.error;
