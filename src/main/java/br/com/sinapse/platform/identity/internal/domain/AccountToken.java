package br.com.sinapse.platform.identity.internal.domain;

import br.com.sinapse.platform.identity.internal.error.InvalidTokenException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A single-use, expiring credential delivered to the account holder.
 *
 * <p>Only the SHA-256 hash of the token is stored; the value itself exists once, on its
 * way to the holder, and is never recoverable from the database. Consuming the token is a
 * single write of {@code consumedAt}, so a token that was replayed and a token that was
 * never issued fail the same way.
 */
@Entity
@Table(name = "account_token")
public class AccountToken {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, updatable = false)
    private AccountTokenPurpose purpose;

    @Column(name = "token_hash", nullable = false, updatable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** For JPA. */
    protected AccountToken() {
    }

    /**
     * Issues a token.
     *
     * @param id        identifier of the record
     * @param accountId holder the token was issued to
     * @param purpose   what the token authorises
     * @param tokenHash SHA-256 hash of the value delivered to the holder
     * @param createdAt instant of issuance
     * @param expiresAt instant from which the token is worthless
     */
    public AccountToken(UUID id, UUID accountId, AccountTokenPurpose purpose, String tokenHash,
            Instant createdAt, Instant expiresAt) {
        this.id = id;
        this.accountId = accountId;
        this.purpose = purpose;
        this.tokenHash = tokenHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    /** Identifier of this token record. */
    public UUID id() {
        return id;
    }

    /** Holder the token was issued to. */
    public UUID accountId() {
        return accountId;
    }

    /** What the token authorises. */
    public AccountTokenPurpose purpose() {
        return purpose;
    }

    /** Instant the token stops being usable. */
    public Instant expiresAt() {
        return expiresAt;
    }

    /** Instant the token was used, or {@code null} while unused. */
    public Instant consumedAt() {
        return consumedAt;
    }

    /** Instant the token was issued. */
    public Instant createdAt() {
        return createdAt;
    }

    /**
     * Spends the token.
     *
     * @param purposeClaimed purpose the caller believes the token has, checked so that a
     *                       password reset token cannot be presented as an e-mail
     *                       verification
     * @param now            current instant
     * @throws InvalidTokenException if the purpose does not match, or the token was
     *                               already consumed, or it has expired
     */
    public void consume(AccountTokenPurpose purposeClaimed, Instant now) {
        if (purpose != purposeClaimed || consumedAt != null || !now.isBefore(expiresAt)) {
            throw new InvalidTokenException();
        }
        this.consumedAt = now;
    }
}
