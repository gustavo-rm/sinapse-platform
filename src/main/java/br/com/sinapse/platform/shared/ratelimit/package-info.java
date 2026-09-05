/**
 * Rate limiting (ADR 0009).
 *
 * <p>One mechanism, one filter class, policy declared per route in configuration.
 * The key is the account identifier when the caller is authenticated and the client
 * address when it is not.
 *
 * <p>The client address comes from the infrastructure, never from a header the client
 * controls. {@code X-Forwarded-For} is read only when the request arrives from an
 * explicitly configured trusted proxy — a forged-header bypass of exactly this kind
 * was already found in the Sinapse Core repository.
 */
package br.com.sinapse.platform.shared.ratelimit;
