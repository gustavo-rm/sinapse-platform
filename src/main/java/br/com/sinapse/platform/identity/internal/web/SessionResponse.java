package br.com.sinapse.platform.identity.internal.web;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * The session just opened.
 *
 * <p>The token is in the body <em>and</em> in a cookie, and they are the same token. A
 * browser uses the cookie and ignores the body; a client that is not a browser uses the body
 * and sends the value back as {@code Authorization: Bearer}. ADR 0010 chose to serve both
 * rather than force a browser to store a token where a script can read it.
 *
 * @param sessionId         identifier of the session, which is what ends it
 * @param token             opaque value; the server keeps only its hash and can never show it
 *                          again
 * @param absoluteExpiresAt when the session dies regardless of activity
 */
@Schema(description = "An opened session and the opaque token that identifies it")
public record SessionResponse(UUID sessionId, String token, Instant absoluteExpiresAt) {
}
