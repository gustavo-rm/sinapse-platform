package br.com.sinapse.platform.planning.internal.domain;

import br.com.sinapse.platform.planning.api.GenerationRequestStatus;
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
     * @param id            identifier
     * @param accountId     student
     * @param horizonStart  first day of the horizon, inclusive
     * @param horizonEnd    last day of the horizon
     * @param requestedAt   when it was asked for
     */
    public PlanGenerationRequest(UUID id, UUID accountId, LocalDate horizonStart,
            LocalDate horizonEnd, Instant requestedAt) {
        this.id = id;
        this.accountId = accountId;
        this.status = GenerationRequestStatus.PENDING;
        this.horizonStart = horizonStart;
        this.horizonEnd = horizonEnd;
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
}
