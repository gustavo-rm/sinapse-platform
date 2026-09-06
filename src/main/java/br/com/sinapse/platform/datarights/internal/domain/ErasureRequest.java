package br.com.sinapse.platform.datarights.internal.domain;

import br.com.sinapse.platform.datarights.api.ErasureRequestStatus;
import br.com.sinapse.platform.datarights.internal.error.ErasureRequestNotOpenException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A request to erase an account, and what became of it.
 *
 * <p><strong>It holds no copy of what was erased and no e-mail.</strong> That is not an
 * omission to be corrected later: this row is designed to outlive the erasure, so anything
 * personal on it would be the one thing the request was made to remove, kept in the record of
 * removing it. What it holds is the account identifier — which is the primary key of a shell
 * that carries nothing — and four instants.
 *
 * <p>The seven days between the request and its effect are the reversibility ADR 0011 asks for.
 * The account is suspended for all of them, so the delay costs the holder nothing they were
 * still using, and it protects against a request made by accident or under pressure.
 */
@Entity
@Table(name = "erasure_request")
public class ErasureRequest {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ErasureRequestStatus status;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "effective_at", nullable = false, updatable = false)
    private Instant effectiveAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    /**
     * Why an attempt failed, from a closed set and never a message.
     *
     * <p>A free-text reason here would be a place for an exception message to land, and an
     * exception message from the middle of an erasure would quote the data being erased.
     */
    @Column(name = "failure_reason")
    private String failureReason;

    /** For JPA. */
    protected ErasureRequest() {
    }

    /**
     * Makes a request.
     *
     * @param id          identifier
     * @param accountId   holder asking to be erased
     * @param requestedAt when it was made
     * @param effectiveAt when it takes effect, which must be after it was made
     */
    public ErasureRequest(UUID id, UUID accountId, Instant requestedAt, Instant effectiveAt) {
        if (!effectiveAt.isAfter(requestedAt)) {
            throw new IllegalArgumentException("an erasure request cannot take effect at once");
        }
        this.id = id;
        this.accountId = accountId;
        this.status = ErasureRequestStatus.REQUESTED;
        this.requestedAt = requestedAt;
        this.effectiveAt = effectiveAt;
    }

    /** Identifier of the request. */
    public UUID id() {
        return id;
    }

    /** Holder asking to be erased. */
    public UUID accountId() {
        return accountId;
    }

    /** Where the request stands. */
    public ErasureRequestStatus status() {
        return status;
    }

    /** When it was made. */
    public Instant requestedAt() {
        return requestedAt;
    }

    /** When it takes effect, and until when it can be withdrawn. */
    public Instant effectiveAt() {
        return effectiveAt;
    }

    /** When it was carried out, or {@code null}. */
    public Instant completedAt() {
        return completedAt;
    }

    /** When it was withdrawn, or {@code null}. */
    public Instant cancelledAt() {
        return cancelledAt;
    }

    /** Why an attempt failed, or {@code null}. */
    public String failureReason() {
        return failureReason;
    }

    /** Whether the window is still open. */
    public boolean isOpen() {
        return status.isOpen();
    }

    /**
     * Whether the window has elapsed.
     *
     * @param now current instant
     * @return whether the request is due to be carried out
     */
    public boolean isDueAt(Instant now) {
        return isOpen() && !now.isBefore(effectiveAt);
    }

    /**
     * Marks the request as carried out.
     *
     * @param at instant the erasure completed
     * @throws ErasureRequestNotOpenException if the window is no longer open
     */
    public void complete(Instant at) {
        requireOpen();
        this.status = ErasureRequestStatus.COMPLETED;
        this.completedAt = at;
    }

    /**
     * Withdraws the request.
     *
     * @param at instant of the withdrawal
     * @throws ErasureRequestNotOpenException if the window is no longer open, which after seven
     *                                        days means the data is already gone
     */
    public void cancel(Instant at) {
        requireOpen();
        this.status = ErasureRequestStatus.CANCELLED;
        this.cancelledAt = at;
    }

    /**
     * Marks an attempt as failed.
     *
     * <p>Nothing was erased: the whole attempt rolled back, because partial erasure is worse
     * than none. The request is left for a person to look at rather than retried on a schedule.
     *
     * @param reason which kind of failure it was
     * @throws ErasureRequestNotOpenException if the window is no longer open
     */
    public void fail(String reason) {
        requireOpen();
        this.status = ErasureRequestStatus.FAILED;
        this.failureReason = reason;
    }

    private void requireOpen() {
        if (!isOpen()) {
            throw new ErasureRequestNotOpenException();
        }
    }
}
