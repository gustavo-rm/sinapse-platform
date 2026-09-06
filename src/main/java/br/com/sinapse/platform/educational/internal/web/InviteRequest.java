package br.com.sinapse.platform.educational.internal.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import java.time.Duration;

/**
 * What issuing an invite submits.
 *
 * @param lifetime how long the code should last. Omitted means the configured default;
 *                 there is no way to ask for a code that never expires
 * @param maxUses  how many students may redeem it, or omitted for no limit
 */
@Schema(description = "An invite to issue")
public record InviteRequest(
        @Schema(example = "P14D", description = "ISO-8601 duration. Omit for the configured default")
        Duration lifetime,

        @Positive Integer maxUses) {
}
