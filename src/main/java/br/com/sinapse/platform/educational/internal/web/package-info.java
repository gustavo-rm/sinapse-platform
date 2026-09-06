/**
 * The HTTP surface of the educational module.
 *
 * <p>Every route lives under {@code /api/v1} and every error body comes from the shared
 * catalogue. No state-changing operation is reachable by GET — the CSRF decision of ADR 0009
 * depends on that, and a test walks every GET handler to keep it true.
 */
package br.com.sinapse.platform.educational.internal.web;
