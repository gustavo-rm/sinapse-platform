/**
 * Adapter for the Sinapse Core, the genetic-algorithm optimiser that runs as a
 * separate process and is consumed over HTTP.
 *
 * <p><strong>It depends on nothing in this application.</strong> The declaration below says so
 * and the build enforces it. That is what makes {@code contract} liftable into the versioned
 * artifact ADR 0002 calls for: a package that reached into {@code shared} or into another
 * module's {@code api} could not be published to the core's own repository without dragging
 * the backend along.
 *
 * <p>Two published surfaces. {@code contract} is the wire format, shared with the other
 * repository and versioned with it. {@code api} is how this application calls it, and stays.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Core Client", allowedDependencies = {})
package br.com.sinapse.platform.coreclient;
