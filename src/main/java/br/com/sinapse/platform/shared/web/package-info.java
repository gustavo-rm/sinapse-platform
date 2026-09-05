/**
 * Web layer shared by every module: the versioned route prefix, the resolution of the
 * address a request came from, the error contract and the diagnostic probe used to prove
 * that both behave as specified.
 *
 * <p>Published as a named interface because a domain module cannot honour ADR 0009
 * without it: the route prefix has a single definition, and the address a request is
 * attributed to is decided in one place rather than re-derived from a header per module.
 */
@org.springframework.modulith.NamedInterface("web")
package br.com.sinapse.platform.shared.web;
