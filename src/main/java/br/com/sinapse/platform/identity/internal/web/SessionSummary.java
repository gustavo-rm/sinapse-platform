package br.com.sinapse.platform.identity.internal.web;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * One of the caller's own sessions, as shown in the list they can end sessions from.
 *
 * <p>No token, obviously, and no address either: the address is stored as a hash and a hash
 * would tell the holder nothing. What is left is what actually helps someone recognise a
 * session — when it started, when it was last used, and which client it is.
 *
 * @param id                identifier, which is what ends this session
 * @param createdAt         when it was opened
 * @param lastSeenAt        when it was last used
 * @param absoluteExpiresAt when it dies regardless of activity
 * @param userAgent         client that opened it, as the client described itself
 * @param current           whether this is the session the request arrived on
 */
@Schema(description = "A session of the caller")
public record SessionSummary(
        UUID id,
        Instant createdAt,
        Instant lastSeenAt,
        Instant absoluteExpiresAt,
        String userAgent,
        boolean current) {
}
