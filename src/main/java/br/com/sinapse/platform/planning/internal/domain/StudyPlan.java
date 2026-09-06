package br.com.sinapse.platform.planning.internal.domain;

import br.com.sinapse.platform.planning.api.PlanStatus;
import br.com.sinapse.platform.planning.api.PlannedSessionKind;
import br.com.sinapse.platform.planning.internal.error.PlanAlreadySupersededException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A plan the optimisation core produced, and the root of its scheduled sessions.
 *
 * <p><strong>Immutable except for its supersession.</strong> Three reasons, from ADR 0007. A
 * plan is the output of a stochastic process, and editing it destroys the correspondence with
 * the snapshot that produced it, which is what makes it reproducible at all. Executed study
 * sessions reference its planned sessions, and mutating those would corrupt the evidence. And
 * the chain of superseded plans is experimental data about how often and at what point in the
 * horizon a student re-plans, obtained for nothing.
 *
 * <p>Every field that must not move is mapped {@code updatable = false}, so Hibernate never
 * puts it in an update statement. A trigger refuses the same set underneath, which is what
 * catches a mapping written later by somebody who did not read this.
 *
 * <p>{@code fitness} is stored as the core reported it and is not normalised. Normalising it
 * would create a second copy of a model this module does not own, to be kept in sync with the
 * first for no gain.
 */
@Entity
@Table(name = "study_plan")
public class StudyPlan {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "generation_request_id", nullable = false, updatable = false)
    private UUID generationRequestId;

    @Column(name = "horizon_start", nullable = false, updatable = false)
    private LocalDate horizonStart;

    @Column(name = "horizon_end", nullable = false, updatable = false)
    private LocalDate horizonEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PlanStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "fitness", updatable = false)
    private Map<String, Object> fitness;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "superseded_at")
    private Instant supersededAt;

    @Column(name = "superseded_by_plan_id")
    private UUID supersededByPlanId;

    /**
     * The sessions of the plan, in the order the core sequenced them.
     *
     * <p>Cascades on persist only. There is no orphan removal and no cascade on remove,
     * because a planned session is never deleted — a trigger refuses it, and a mapping that
     * offered the operation would only be a way of finding that out at runtime.
     */
    @OneToMany(mappedBy = "plan", cascade = CascadeType.PERSIST, fetch = FetchType.LAZY)
    @OrderBy("sequenceIndex")
    private List<PlannedSession> sessions = new ArrayList<>();

    /** For JPA. */
    protected StudyPlan() {
    }

    /**
     * Records a generated plan, in force from the moment it is stored.
     *
     * @param id                  identifier
     * @param accountId           student
     * @param generationRequestId job that produced it
     * @param horizonStart        first day of the horizon, inclusive
     * @param horizonEnd          last day of the horizon
     * @param fitness             metrics the core reported, or {@code null}
     * @param createdAt           when it was stored
     */
    public StudyPlan(UUID id, UUID accountId, UUID generationRequestId, LocalDate horizonStart,
            LocalDate horizonEnd, Map<String, Object> fitness, Instant createdAt) {
        this.id = id;
        this.accountId = accountId;
        this.generationRequestId = generationRequestId;
        this.horizonStart = horizonStart;
        this.horizonEnd = horizonEnd;
        this.status = PlanStatus.ACTIVE;
        this.fitness = fitness == null ? null : Map.copyOf(fitness);
        this.createdAt = createdAt;
    }

    /** Identifier of the plan. */
    public UUID id() {
        return id;
    }

    /** Student the plan belongs to. */
    public UUID accountId() {
        return accountId;
    }

    /** Job that produced it. */
    public UUID generationRequestId() {
        return generationRequestId;
    }

    /** First day of the horizon, inclusive. */
    public LocalDate horizonStart() {
        return horizonStart;
    }

    /** Last day of the horizon. */
    public LocalDate horizonEnd() {
        return horizonEnd;
    }

    /** In force, or replaced. */
    public PlanStatus status() {
        return status;
    }

    /** Metrics the core reported, or {@code null}. */
    public Map<String, Object> fitness() {
        return fitness == null ? null : Collections.unmodifiableMap(fitness);
    }

    /** When it was stored. */
    public Instant createdAt() {
        return createdAt;
    }

    /** When it was replaced, or {@code null}. */
    public Instant supersededAt() {
        return supersededAt;
    }

    /** The plan that replaced it, or {@code null}. */
    public UUID supersededByPlanId() {
        return supersededByPlanId;
    }

    /** Whether this is the plan in force. */
    public boolean isActive() {
        return status == PlanStatus.ACTIVE;
    }

    /** The sessions of the plan, in sequence. */
    public List<PlannedSession> sessions() {
        return Collections.unmodifiableList(sessions);
    }

    /**
     * Adds a session to the plan.
     *
     * <p>Only while the plan is being assembled. Once it is stored, a trigger refuses the
     * insert of anything the plan was not born with — the whole plan is the unit, not the
     * session.
     *
     * @param topicId         topic to be studied
     * @param kind            new ground or going back over it
     * @param scheduledStart  when the core placed it
     * @param durationMinutes how long it allowed for it
     * @param sequenceIndex   its position in the plan
     * @return the session
     */
    public PlannedSession schedule(UUID topicId, PlannedSessionKind kind, Instant scheduledStart,
            int durationMinutes, int sequenceIndex) {

        PlannedSession session = new PlannedSession(UUID.randomUUID(), this, topicId, kind,
                scheduledStart, durationMinutes, sequenceIndex);
        sessions.add(session);
        return session;
    }

    /**
     * Marks the plan as replaced.
     *
     * <p>Done in two steps by the service, and not out of fussiness: the successor's row has
     * to exist before this plan may point at it, and this plan has to stop being active before
     * the successor may be inserted, or the partial index refuses it. So the status and the
     * instant are written first, and {@link #supersededBy} follows once the successor exists.
     *
     * @param at instant it was replaced
     * @throws PlanAlreadySupersededException if it has already been replaced, because moving
     *                                        the instant would rewrite when the student
     *                                        actually changed plan
     */
    public void supersede(Instant at) {
        if (status == PlanStatus.SUPERSEDED) {
            throw new PlanAlreadySupersededException();
        }
        this.status = PlanStatus.SUPERSEDED;
        this.supersededAt = at;
    }

    /**
     * Records which plan replaced this one.
     *
     * @param successorPlanId the plan that took over
     */
    public void supersededBy(UUID successorPlanId) {
        this.supersededByPlanId = successorPlanId;
    }
}
