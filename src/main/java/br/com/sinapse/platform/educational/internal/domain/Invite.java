package br.com.sinapse.platform.educational.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A code that puts a student into a classroom.
 *
 * <p>A root of its own because of who looks it up: a student who has no access to the
 * classroom yet, searching by code. Reaching it through the classroom would mean reading a
 * classroom the caller is not allowed to read.
 *
 * <p><strong>The code is stored in clear text.</strong> That is a conscious trade-off and not
 * an oversight: hashing it would stop the teacher from displaying it again after creation,
 * which is how the code is actually used. What compensates is the code being long, expiring,
 * revocable, and worth little on its own — redeeming an invite grants access to nothing, it
 * only puts the student in a classroom, and it still requires an active account and a valid
 * sharing consent.
 *
 * <p>What that trade-off <em>does</em> require is rate limiting on redemption attempts. Fifty
 * bits of entropy protect nothing against a caller who may guess without limit. Section 7.2 of
 * the architecture document states this as a condition of the design, not as later hardening.
 */
@Entity
@Table(name = "invite")
public class Invite {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "classroom_id", nullable = false, updatable = false)
    private UUID classroomId;

    /** Canonical, uppercase, unique across the whole table including expired and revoked. */
    @Column(name = "code", nullable = false, updatable = false)
    private String code;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    /** {@code null} means no limit on how many students may redeem it. */
    @Column(name = "max_uses", updatable = false)
    private Integer maxUses;

    @Column(name = "use_count", nullable = false)
    private int useCount;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    /** For JPA. */
    protected Invite() {
    }

    /**
     * Issues an invite.
     *
     * @param id          identifier
     * @param classroomId classroom it admits to
     * @param code        canonical code
     * @param createdBy   teacher who issued it
     * @param createdAt   instant of issuance
     * @param expiresAt   instant it stops working. Mandatory: an invite that never expires is
     *                    a credential nobody remembers exists
     * @param maxUses     how many redemptions it allows, or {@code null} for no limit
     */
    public Invite(UUID id, UUID classroomId, String code, UUID createdBy, Instant createdAt,
            Instant expiresAt, Integer maxUses) {
        this.id = id;
        this.classroomId = classroomId;
        this.code = code;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.maxUses = maxUses;
        this.useCount = 0;
    }

    /** Identifier of the invite. */
    public UUID id() {
        return id;
    }

    /** Classroom it admits to. */
    public UUID classroomId() {
        return classroomId;
    }

    /** The code, in clear. */
    public String code() {
        return code;
    }

    /** Teacher who issued it. */
    public UUID createdBy() {
        return createdBy;
    }

    /** Instant of issuance. */
    public Instant createdAt() {
        return createdAt;
    }

    /** Instant it stops working. */
    public Instant expiresAt() {
        return expiresAt;
    }

    /** How many redemptions it allows, or {@code null}. */
    public Integer maxUses() {
        return maxUses;
    }

    /** How many times it has been redeemed. */
    public int useCount() {
        return useCount;
    }

    /** Instant the teacher revoked it, or {@code null}. */
    public Instant revokedAt() {
        return revokedAt;
    }

    /** Whether the teacher has revoked it. */
    public boolean isRevoked() {
        return revokedAt != null;
    }

    /**
     * Whether it has passed its expiry.
     *
     * @param now current instant
     */
    public boolean isExpiredAt(Instant now) {
        return !now.isBefore(expiresAt);
    }

    /** Whether every allowed redemption has been used. */
    public boolean isExhausted() {
        return maxUses != null && useCount >= maxUses;
    }

    /**
     * Whether the invite itself is still good.
     *
     * <p>Says nothing about the classroom, the account or the consent. Those are three other
     * conditions, checked by the service that owns the redemption, and folding them in here
     * would put a rule about accounts inside an entity that knows nothing about accounts.
     *
     * @param now current instant
     */
    public boolean isRedeemableAt(Instant now) {
        return !isRevoked() && !isExpiredAt(now) && !isExhausted();
    }

    /**
     * Records one redemption.
     *
     * @param now instant of the redemption
     * @throws IllegalStateException if the invite is no longer redeemable, which the caller is
     *                               expected to have checked; reaching this means the check
     *                               was skipped
     */
    public void recordRedemption(Instant now) {
        if (!isRedeemableAt(now)) {
            throw new IllegalStateException("Invite " + id + " is not redeemable");
        }
        this.useCount++;
    }

    /**
     * Revokes the invite. Idempotent, so that archiving a classroom does not have to care
     * which of its invites the teacher had already revoked by hand.
     *
     * @param now instant of the revocation
     */
    public void revoke(Instant now) {
        if (revokedAt == null) {
            this.revokedAt = now;
        }
    }
}
