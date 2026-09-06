/**
 * The HTTP surface of the identity module.
 *
 * <p>Every route lives under {@code /api/v1} and every error body comes from the shared
 * catalogue (ADR 0009). No controller composes a message of its own, which is what keeps a
 * date of birth, an address or a token out of a response by construction rather than by
 * each endpoint remembering to.
 *
 * <p>No state-changing operation is reachable by GET. That is not a style preference here:
 * CSRF protection is off, and what stands in for it is {@code SameSite=Strict} on the
 * session cookie, which only holds while GET is safe.
 */
package br.com.sinapse.platform.identity.internal.web;
