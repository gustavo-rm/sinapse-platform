package br.com.sinapse.platform.identity.internal.domain;

import br.com.sinapse.platform.identity.api.ConsentGrantedBy;
import br.com.sinapse.platform.identity.api.ConsentPurpose;
import br.com.sinapse.platform.identity.internal.error.ConsentAlreadyRevokedException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicUpdate;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One act of consent, for one purpose, by one grantor.
 *
 * <p>A root of its own rather than part of the account: it grows without bound and is
 * queried independently of the holder (section 5.1).
 *
 * <p><strong>Append-only.</strong> This is a legal record, and invariant 4 allows exactly
 * one change to it after it is written: a single write of {@code revokedAt}. Every other
 * column is mapped {@code updatable = false}, so Hibernate cannot emit an update for it
 * even if some future code path asks. That mapping is the first line; the second is a
 * trigger in the database, which is there because the mapping is the sort of thing that
 * gets changed by accident.
 *
 * <p>The account is referenced by identifier and not by an association. Within the module
 * an association would be possible, but the record is read on its own — the whole reason
 * it is a separate root — and an association would drag an account into every read of a
 * consent history.
 *
 * <p><strong>{@link DynamicUpdate} is load-bearing.</strong> {@code evidence} and
 * {@code guardian_id} are writable in the mapping only so that an erasure can clear them, and
 * without dynamic updates Hibernate would put both in every update statement — including the
 * one that records a withdrawal. The trigger compares the two documents, the round trip through
 * the converter is not guaranteed to reproduce the stored one byte for byte, and an ordinary
 * withdrawal would then be refused as a rewrite of evidence it never meant to touch.
 */
@Entity
@DynamicUpdate
@Table(name = "consent_record")
public class ConsentRecord {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, updatable = false)
    private ConsentPurpose purpose;

    @Column(name = "terms_version_id", nullable = false, updatable = false)
    private UUID termsVersionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "granted_by", nullable = false, updatable = false)
    private ConsentGrantedBy grantedBy;

    /**
     * Guardian who granted it, or {@code null}.
     *
     * <p>Writable in the mapping only so that an Article 18 erasure can clear it. The
     * database is what guarantees it does not move otherwise: the trigger refuses any change
     * to this column unless the transaction-local erasure flag is set.
     */
    @Column(name = "guardian_id")
    private UUID guardianId;

    @Column(name = "granted_at", nullable = false, updatable = false)
    private Instant grantedAt;

    /** The one column invariant 4 allows to change, and only from {@code null}. */
    @Column(name = "revoked_at")
    private Instant revokedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence")
    private ConsentEvidence evidence;

    /** For JPA. */
    protected ConsentRecord() {
    }

    private ConsentRecord(UUID id, UUID accountId, ConsentPurpose purpose, UUID termsVersionId,
            ConsentGrantedBy grantedBy, UUID guardianId, Instant grantedAt, ConsentEvidence evidence) {
        this.id = id;
        this.accountId = accountId;
        this.purpose = purpose;
        this.termsVersionId = termsVersionId;
        this.grantedBy = grantedBy;
        this.guardianId = guardianId;
        this.grantedAt = grantedAt;
        this.evidence = evidence;
    }

    /**
     * Records a consent granted by the holder.
     *
     * @param id             identifier of the record
     * @param accountId      holder of the data
     * @param purpose        purpose consented to
     * @param termsVersionId wording the holder accepted
     * @param grantedAt      instant of the act
     * @param evidence       what was observed about the act
     * @return the record, valid until revoked
     */
    public static ConsentRecord grantedBySelf(UUID id, UUID accountId, ConsentPurpose purpose,
            UUID termsVersionId, Instant grantedAt, ConsentEvidence evidence) {
        return new ConsentRecord(id, accountId, purpose, termsVersionId, ConsentGrantedBy.SELF, null,
                grantedAt, evidence);
    }

    /**
     * Records a consent granted by a guardian.
     *
     * @param id             identifier of the record
     * @param accountId      holder of the data
     * @param purpose        purpose consented to
     * @param termsVersionId wording the guardian accepted
     * @param guardianId     guardian who granted it
     * @param grantedAt      instant of the act
     * @param evidence       what was observed about the act
     * @return the record, valid until revoked
     */
    public static ConsentRecord grantedByGuardian(UUID id, UUID accountId, ConsentPurpose purpose,
            UUID termsVersionId, UUID guardianId, Instant grantedAt, ConsentEvidence evidence) {
        return new ConsentRecord(id, accountId, purpose, termsVersionId, ConsentGrantedBy.GUARDIAN,
                guardianId, grantedAt, evidence);
    }

    /** Identifier of this record. */
    public UUID id() {
        return id;
    }

    /** Holder whose data the consent covers. */
    public UUID accountId() {
        return accountId;
    }

    /** Purpose consented to. */
    public ConsentPurpose purpose() {
        return purpose;
    }

    /** Wording accepted. */
    public UUID termsVersionId() {
        return termsVersionId;
    }

    /** Who granted the consent. */
    public ConsentGrantedBy grantedBy() {
        return grantedBy;
    }

    /** Guardian who granted it, or {@code null} when the holder did. */
    public UUID guardianId() {
        return guardianId;
    }

    /** Instant of the act. */
    public Instant grantedAt() {
        return grantedAt;
    }

    /** Instant of the revocation, or {@code null} while the consent is valid. */
    public Instant revokedAt() {
        return revokedAt;
    }

    /** What was observed about the act. Personal data: never log it. */
    public ConsentEvidence evidence() {
        return evidence;
    }

    /**
     * Removes the personal data from the record, keeping the record.
     *
     * <p>Only ever called from the erasure transaction, and the database enforces that: the
     * trigger refuses either column to move unless the erasure flag is set.
     *
     * <p>What survives is the fact — which purpose, which wording, granted by whom, when, and
     * whether it was withdrawn. That is what proves the legal basis for treatment that already
     * happened, and the burden of that proof is the controller's. What does not survive is the
     * address and the agent string, which prove nothing about the fact and are personal data
     * belonging to the holder, and the guardian, who is a third party with no basis for
     * retention once the holder's data is gone.
     */
    public void clearPersonalData() {
        this.evidence = null;
        this.guardianId = null;
    }

    /** Whether the consent is currently in force. */
    public boolean isValid() {
        return revokedAt == null;
    }

    /**
     * Ends the consent.
     *
     * <p>Ending something means writing a timestamp. Nothing is deleted and nothing else
     * is touched, which is what makes the record still provable afterwards: it shows both
     * that consent was given and that it was withdrawn.
     *
     * @param at instant of the revocation
     * @throws ConsentAlreadyRevokedException if the record was already revoked, because a
     *                                        second write of the same column is the very
     *                                        thing invariant 4 forbids
     */
    public void revoke(Instant at) {
        if (revokedAt != null) {
            throw new ConsentAlreadyRevokedException();
        }
        this.revokedAt = at;
    }
}
