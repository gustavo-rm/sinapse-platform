package br.com.sinapse.platform.planning.internal.domain;

import br.com.sinapse.platform.planning.api.GenerationRequestStatus;
import br.com.sinapse.platform.planning.api.PlanGenerationFailure;
import br.com.sinapse.platform.planning.internal.error.GenerationRequestNotRunningException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * The asynchronous job that asks the core for a plan.
 *
 * <p><strong>Mapped, not executed.</strong> Nothing in this version claims a job, calls the
 * core or assembles a snapshot; the table exists and this is the shape of a row in it. The
 * consumer, the snapshot and the client are the next step of the build.
 *
 * <p>The table is also the queue (ADR 0007). Workers will claim rows with
 * {@code select ... for update skip locked}; a broker would be infrastructure to operate,
 * monitor and debug without a problem at the pilot's scale that justifies it, and if scale
 * ever demands one the change is confined to the consumer.
 *
 * <p><strong>Four fields, not one, are what make a plan reproducible</strong>: the snapshot,
 * the core version, the algorithm parameters and the random seed. A genetic algorithm is
 * stochastic, so the same snapshot and the same parameters produce a different plan unless
 * the seed is fixed. Without all four a plan generated today cannot be regenerated tomorrow,
 * because availability and history will have moved. This is a requirement of the thesis, not
 * audit comfort.
 *
 * <p>The snapshot holds the exact payload sent, in JSON, deliberately not normalised.
 * Normalising it would create a second copy of the domain model to be kept in sync with the
 * first, for nothing.
 */
@Entity
@Table(name = "plan_generation_request")
public class PlanGenerationRequest {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private GenerationRequestStatus status;

    @Column(name = "horizon_start", nullable = false, updatable = false)
    private LocalDate horizonStart;

    @Column(name = "horizon_end", nullable = false, updatable = false)
    private LocalDate horizonEnd;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot")
    private Map<String, Object> snapshot;

    @Column(name = "core_version")
    private String coreVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "algorithm_params")
    private Map<String, Object> algorithmParams;

    @Column(name = "random_seed")
    private Long randomSeed;

    /**
     * Which curated state of the catalogue produced this plan.
     *
     * <p>Held as an identifier: the import is curriculum's, and no association crosses a
     * module boundary. Needed to attribute an experimental result to a curation state — the
     * snapshot carries the edges but not their revision.
     */
    @Column(name = "catalog_import_id", updatable = false)
    private UUID catalogImportId;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "failure_reason")
    private String failureReason;

    /** For JPA. */
    protected PlanGenerationRequest() {
    }

    /**
     * Queues a request.
     *
     * @param id              identifier
     * @param accountId       student
     * @param horizonStart    first day of the horizon, inclusive
     * @param horizonEnd      last day of the horizon
     * @param catalogImportId curated catalogue state in effect, or {@code null} when the
     *                        catalogue has never been imported. Recorded at this moment and
     *                        never afterwards: the point is which revision produced the edges
     *                        this run will see
     * @param requestedAt     when it was asked for
     */
    public PlanGenerationRequest(UUID id, UUID accountId, LocalDate horizonStart,
            LocalDate horizonEnd, UUID catalogImportId, Instant requestedAt) {
        this.id = id;
        this.accountId = accountId;
        this.status = GenerationRequestStatus.PENDING;
        this.horizonStart = horizonStart;
        this.horizonEnd = horizonEnd;
        this.catalogImportId = catalogImportId;
        this.requestedAt = requestedAt;
        this.attemptCount = 0;
    }

    /** Identifier of the request. */
    public UUID id() {
        return id;
    }

    /** Student the plan is for. */
    public UUID accountId() {
        return accountId;
    }

    /** Where the job stands. */
    public GenerationRequestStatus status() {
        return status;
    }

    /** First day of the horizon, inclusive. */
    public LocalDate horizonStart() {
        return horizonStart;
    }

    /** Last day of the horizon. */
    public LocalDate horizonEnd() {
        return horizonEnd;
    }

    /** The exact payload sent to the core, or {@code null} before it was sent. */
    public Map<String, Object> snapshot() {
        return snapshot;
    }

    /** Version of the core that ran, or {@code null}. */
    public String coreVersion() {
        return coreVersion;
    }

    /** Parameters the algorithm ran with, or {@code null}. */
    public Map<String, Object> algorithmParams() {
        return algorithmParams;
    }

    /** Seed the pseudo-random generator ran with, or {@code null}. */
    public Long randomSeed() {
        return randomSeed;
    }

    /** Curated catalogue state used, or {@code null}. */
    public UUID catalogImportId() {
        return catalogImportId;
    }

    /** When it was asked for. */
    public Instant requestedAt() {
        return requestedAt;
    }

    /** When a worker claimed it, or {@code null}. */
    public Instant startedAt() {
        return startedAt;
    }

    /** When it finished, or {@code null}. */
    public Instant finishedAt() {
        return finishedAt;
    }

    /** How many times it has been attempted. */
    public int attemptCount() {
        return attemptCount;
    }

    /** Why it failed, or {@code null}. */
    public String failureReason() {
        return failureReason;
    }

    /** Whether the job has finished, whatever the outcome. */
    public boolean isTerminal() {
        return status.isTerminal();
    }

    /**
     * Marks the job as claimed by a worker.
     *
     * <p>The attempt is counted here rather than on failure, so that a worker which dies
     * without recording anything still spends one. The alternative loses the count exactly in
     * the case the count exists for.
     *
     * @param at instant it was claimed
     */
    public void claim(Instant at) {
        this.status = GenerationRequestStatus.RUNNING;
        this.startedAt = at;
        this.attemptCount = attemptCount + 1;
    }

    /**
     * Records what was sent to the core, before it is sent.
     *
     * <p>Written first on purpose. A run that dies mid-call still leaves the record of what was
     * attempted, and a snapshot stored only on success would be a snapshot of the runs that
     * happened to work.
     *
     * @param snapshot        the exact document sent
     * @param algorithmParams parameters the run was asked to use
     * @param randomSeed      seed the run was asked to use, chosen by this backend
     */
    public void recordSubmission(Map<String, Object> snapshot, Map<String, Object> algorithmParams,
            long randomSeed) {
        requireRunning();
        this.snapshot = snapshot == null ? null : Map.copyOf(snapshot);
        this.algorithmParams = algorithmParams == null ? null : Map.copyOf(algorithmParams);
        this.randomSeed = randomSeed;
    }

    /**
     * Closes the job as finished, with the version that produced the plan.
     *
     * <p>The fourth of the four fields that make a plan reproducible, and the only one that
     * could not be known before the call.
     *
     * @param at          instant it finished
     * @param coreVersion version of the optimiser that ran
     */
    public void succeed(Instant at, String coreVersion) {
        requireRunning();
        this.status = GenerationRequestStatus.READY;
        this.finishedAt = at;
        this.coreVersion = coreVersion;
    }

    /**
     * Closes the job as failed.
     *
     * <p>The reason is a value from a closed set and never a message. What actually happened is
     * in the log; a client learns which kind of failure it was, which is what it can act on,
     * and nothing about the core's address, its response or the payload that was sent.
     *
     * @param at     instant it finished
     * @param reason which kind of failure it was
     */
    public void fail(Instant at, PlanGenerationFailure reason) {
        requireRunning();
        this.status = GenerationRequestStatus.FAILED;
        this.finishedAt = at;
        this.failureReason = reason.name();
    }

    /**
     * Puts the job back in the queue for another attempt.
     *
     * <p>{@code startedAt} is deliberately left where it is: it is the instant of the last
     * attempt, and the claim query computes the backoff from it. Clearing it would make every
     * failed job immediately eligible again, which is a retry policy of "at once, forever".
     */
    public void releaseForRetry() {
        requireRunning();
        this.status = GenerationRequestStatus.PENDING;
    }

    private void requireRunning() {
        if (status != GenerationRequestStatus.RUNNING) {
            throw new GenerationRequestNotRunningException();
        }
    }
}
