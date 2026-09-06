package br.com.sinapse.platform.educational.internal.web;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * An invite as its own teacher sees it.
 *
 * <p>The code is here, in clear. That is the whole reason it is not hashed: a teacher has to
 * be able to read it out again after creating it. The route is restricted to the classroom's
 * owner, which is what keeps that from being a disclosure.
 *
 * @param id        identifier
 * @param code      the code, in clear
 * @param expiresAt when it stops working
 * @param maxUses   how many redemptions it allows, or {@code null}
 * @param useCount  how many it has had
 * @param revokedAt when the teacher revoked it, or {@code null}
 * @param active    whether it would admit somebody right now
 */
@Schema(description = "An invite, with its code")
public record InviteResponse(
        UUID id,
        String code,
        Instant expiresAt,
        Integer maxUses,
        int useCount,
        Instant revokedAt,
        boolean active) {
}
