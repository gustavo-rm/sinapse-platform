/**
 * The RFC 7807 error contract (ADR 0009).
 *
 * <p>Every error body in the application is built from {@link
 * br.com.sinapse.platform.shared.web.problem.ApiErrorType}, a closed catalogue of
 * pre-written texts. No exception message, no field value and no internal name
 * ever reaches the response: the leak is prevented by construction rather than by
 * each endpoint remembering the rule.
 *
 * <p>Published as a named interface: a domain module raises its failures as an
 * {@link br.com.sinapse.platform.shared.web.problem.ApiException} carrying a catalogue
 * entry, which is precisely what stops it from writing a message of its own.
 */
@org.springframework.modulith.NamedInterface("problem")
package br.com.sinapse.platform.shared.web.problem;
