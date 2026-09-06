/**
 * The port through which the platform reaches the Sinapse Core, and the failures it can meet.
 *
 * <p>Separate from {@code contract} because the two have different lifetimes. The contract is
 * a versioned artifact shared with the core's own repository; this is how <em>this</em>
 * application calls it, and it stays here.
 */
@org.springframework.modulith.NamedInterface("api")
package br.com.sinapse.platform.coreclient.api;
