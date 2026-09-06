package br.com.sinapse.platform.identity.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * A server-side session, identified by an opaque token nobody but the holder ever sees.
 *
 * <p>ADR 0010 rejected a stateless token for one reason: revoking an essential consent
 * suspends the account in the same transaction, and the access gate is what makes that
 * suspension effective. A stateless token would keep working until it expired, leaving the
 * gate inoperative in the meantime — and the gate is the compliance mechanism.
 *
 * <p>Only the SHA-256 hash of the token is stored. Unlike an invite code, which a teacher
 * has to be able to show again, a session token is never displayed a second time, so a
 * leak of this table produces no usable session.
 *
 * <p>Two expiries, both enforced here: idle, counted from the last request, and absolute,
 * counted from creation and never extended.
 */
@Entity
@Table(name = "user_session")
public class UserSession {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "token_hash", nullable = false, updatable = false)
    private String tokenHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "absolute_expires_at", nullable = false, updatable = false)
    private Instant absoluteExpiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "user_agent")
    private String userAgent;

    /**
     * Hash of the address the session was opened from, kept so that the holder can
     * recognise a session in the list and end it. Personal data, and part of the pending
     * retention policy (J3).
     */
    @Column(name = "ip_hash")
    private String ipHash;

    /** For JPA. */
    protected UserSession() {
    }

    /**
     * Opens a session.
     *
     * @param id                identifier of the session
     * @param accountId         holder the session belongs to
     * @param tokenHash         SHA-256 hash of the opaque token handed to the client
     * @param createdAt         instant the session was opened
     * @param absoluteExpiresAt instant the session dies regardless of activity
     * @param userAgent         agent string of the client, truncated
     * @param ipHash            hash of the address the session was opened from
     */
    public UserSession(UUID id, UUID accountId, String tokenHash, Instant createdAt,
            Instant absoluteExpiresAt, String userAgent, String ipHash) {
        this.id = id;
        this.accountId = accountId;
        this.tokenHash = tokenHash;
        this.createdAt = createdAt;
        this.lastSeenAt = createdAt;
        this.absoluteExpiresAt = absoluteExpiresAt;
        this.userAgent = userAgent;
        this.ipHash = ipHash;
    }

    /** Identifier of this session. */
    public UUID id() {
        return id;
    }

    /** Holder the session belongs to. */
    public UUID accountId() {
        return accountId;
    }

    /** Instant the session was opened. */
    public Instant createdAt() {
        return createdAt;
    }

    /** Instant of the last request that used the session. */
    public Instant lastSeenAt() {
        return lastSeenAt;
    }

    /** Instant the session dies regardless of activity. */
    public Instant absoluteExpiresAt() {
        return absoluteExpiresAt;
    }

    /** Instant the session was revoked, or {@code null}. */
    public Instant revokedAt() {
        return revokedAt;
    }

    /** Agent string of the client that opened the session. */
    public String userAgent() {
        return userAgent;
    }

    /** Hash of the address the session was opened from. */
    public String ipHash() {
        return ipHash;
    }

    /**
     * Whether the session may still authenticate a request.
     *
     * @param now         current instant
     * @param idleTimeout how long a session survives without being used
     * @return {@code true} if it is neither revoked, nor idle for too long, nor past its
     *         absolute expiry
     */
    public boolean isUsableAt(Instant now, Duration idleTimeout) {
        return revokedAt == null
                && now.isBefore(absoluteExpiresAt)
                && now.isBefore(lastSeenAt.plus(idleTimeout));
    }

    /**
     * Records that the session was used.
     *
     * @param now instant of the request
     */
    public void touch(Instant now) {
        this.lastSeenAt = now;
    }

    /**
     * Ends the session. Idempotent, so that revoking every session of an account does not
     * have to care which of them were already closed.
     *
     * @param now instant of the revocation
     */
    public void revoke(Instant now) {
        if (revokedAt == null) {
            this.revokedAt = now;
        }
    }
}
