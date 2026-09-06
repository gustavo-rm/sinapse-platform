/**
 * The failures the educational module raises deliberately.
 *
 * <p>Each carries an entry of the shared catalogue and no message of its own.
 *
 * <p>Several distinct failures share an entry on purpose. An invite that never existed, one
 * that expired, one that was revoked and one whose classroom was archived all answer the same
 * way: telling a caller which of those it was would turn the redemption route into a way of
 * probing which codes are real.
 */
package br.com.sinapse.platform.educational.internal.error;
