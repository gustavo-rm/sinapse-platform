/**
 * Cross-cutting infrastructure shared by every module: the RFC 7807 error
 * contract, API versioning, rate limiting, request correlation and the explicit
 * application time zone.
 *
 * <p>It carries no domain concept. Everything decided here is decided once, in
 * ADR 0009 and ADR 0010, and not re-decided per endpoint.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Shared")
package br.com.sinapse.platform.shared;
