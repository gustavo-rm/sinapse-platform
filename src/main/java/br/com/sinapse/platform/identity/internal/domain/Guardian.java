package br.com.sinapse.platform.identity.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * The guardian of a holder below the configured consent age threshold.
 *
 * <p>Internal entity of the account aggregate: it exists only in relation to one account
 * and is never read on its own.
 *
 * <p><strong>v1 scope.</strong> The structure is complete — the columns, the relation and
 * the state that a verification would write — but the flow that issues the verification
 * token, delivers it to the guardian and consumes the answer is deliberately absent (ADR
 * 0004, "consequência para a v1"). Registration below the threshold is refused, so nothing
 * in v1 constructs this entity. Its verification columns are separate from
 * {@code account_token} on purpose: this token travels to a third party, not to the
 * account holder, and merging the two would lose that distinction.
 */
@Entity
@Table(name = "guardian")
public class Guardian {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private Account account;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "email", nullable = false, columnDefinition = "citext")
    private String email;

    @Column(name = "relationship", nullable = false)
    private String relationship;

    @Column(name = "verification_token_hash")
    private String verificationTokenHash;

    @Column(name = "verification_expires_at")
    private Instant verificationExpiresAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** For JPA. */
    protected Guardian() {
    }

    /**
     * Registers a guardian who has not been verified yet.
     *
     * @param id           identifier of the guardian record
     * @param fullName     name given by the holder. Personal data of a third party
     * @param email        address the verification would be delivered to
     * @param relationship relation to the holder, as declared
     * @param createdAt    instant the guardian was declared
     */
    public Guardian(UUID id, String fullName, String email, String relationship, Instant createdAt) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.relationship = relationship;
        this.createdAt = createdAt;
    }

    /** Identifier of this guardian record. */
    public UUID id() {
        return id;
    }

    /** The account this guardian answers for. */
    public Account account() {
        return account;
    }

    /** Name of the guardian. Personal data: never log it. */
    public String fullName() {
        return fullName;
    }

    /** Address of the guardian. Personal data: never log it. */
    public String email() {
        return email;
    }

    /** Declared relation to the holder. */
    public String relationship() {
        return relationship;
    }

    /** Instant the guardian was verified, or {@code null} while unverified. */
    public Instant verifiedAt() {
        return verifiedAt;
    }

    /** Instant the guardian was declared. */
    public Instant createdAt() {
        return createdAt;
    }

    /**
     * Whether a verification has been completed.
     *
     * <p>Read by the activation rule: a minor whose guardian consented but was never
     * verified waits in {@code PENDING_GUARDIAN_CONSENT}. Nothing in v1 sets this, so in
     * v1 the answer is always {@code false} and the wait never ends — which is the correct
     * behaviour while the guardian flow does not exist.
     */
    public boolean isVerified() {
        return verifiedAt != null;
    }

    void attachTo(Account owner) {
        this.account = owner;
    }
}
