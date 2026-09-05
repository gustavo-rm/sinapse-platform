/**
 * How a request comes to be attributed to an account.
 *
 * <p>A session is an opaque server-side token (ADR 0010), carried either in a cookie, for
 * browsers, or in an {@code Authorization: Bearer} header, for everything else. The two are
 * the same token; only the envelope differs.
 */
package br.com.sinapse.platform.identity.internal.security;
