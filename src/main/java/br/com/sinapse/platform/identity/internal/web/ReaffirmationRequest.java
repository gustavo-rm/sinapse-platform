package br.com.sinapse.platform.identity.internal.web;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A reaffirmation of consent after reaching the age threshold.
 *
 * <p>The token is optional, because there are two ways to arrive: signed in, answering the
 * request the application is showing, or from the message the daily sweep triggered, with a
 * token and no session. Exactly one of the two has to be present, and the endpoint says so
 * rather than the type.
 *
 * @param token value delivered with the reaffirmation request, or {@code null} when the
 *              caller is signed in
 */
@Schema(description = "Reaffirmation of consent in the holder's own name")
public record ReaffirmationRequest(
        @Schema(description = "Present only when the caller is not signed in") String token) {
}
