/**
 * The boundary at which a credential leaves the platform on its way to a person.
 *
 * <p>Three flows depend on delivering a token to the account holder: e-mail verification,
 * password reset and majority reaffirmation. No transport is chosen here, and none is
 * configured in this version — the platform has no mail infrastructure yet. What exists is
 * the port, so that the flows are complete, the token is handed to exactly one place, and
 * the adapter that eventually sends mail is a class rather than an edit spread over three
 * services.
 */
package br.com.sinapse.platform.identity.internal.notification;
