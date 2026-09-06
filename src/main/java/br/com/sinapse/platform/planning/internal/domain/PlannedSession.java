package br.com.sinapse.platform.planning.internal.domain;

import br.com.sinapse.platform.planning.api.PlannedSessionKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/**
 * One session the optimisation core scheduled, inside the plan that produced it.
 *
 * <p>An internal entity of {@link StudyPlan} and not a root of its own. That is the opposite
 * choice from {@code Topic} in the curriculum, and it is deliberate: a planned session is
 * never read without its plan, and the plan is the unit of immutability.
 *
 * <p>{@link Immutable} is not decoration. A trigger refuses every update and every delete on
 * this table, so an entity Hibernate believed it could dirty-check would produce a failure at
 * flush time, far from whatever touched it. Marking it here means Hibernate never issues the
 * statement in the first place, and the trigger stays what it should be — the thing that
 * catches what the code was never supposed to do.
 *
 * <p>The reason for the immutability is that executed study sessions carry the identifier of
 * a planned session, without a foreign key in either direction (rule R2). Editing a planned
 * session would silently rewrite what an executed one says it executed.
 */
@Entity
@Immutable
@Table(name = "planned_session")
public class PlannedSession {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false, updatable = false)
    private StudyPlan plan;

    /** Curriculum's aggregate, held as an identifier: no association crosses a module (R1). */
    @Column(name = "topic_id", nullable = false, updatable = false)
    private UUID topicId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, updatable = false)
    private PlannedSessionKind kind;

    @Column(name = "scheduled_start", nullable = false, updatable = false)
    private Instant scheduledStart;

    @Column(name = "duration_minutes", nullable = false, updatable = false)
    private int durationMinutes;

    @Column(name = "sequence_index", nullable = false, updatable = false)
    private int sequenceIndex;

    /** For JPA. */
    protected PlannedSession() {
    }

    /**
     * Schedules a session inside a plan.
     *
     * @param id              identifier
     * @param plan            plan it belongs to
     * @param topicId         topic to be studied
     * @param kind            new ground or going back over it
     * @param scheduledStart  when the core placed it
     * @param durationMinutes how long it allowed for it
     * @param sequenceIndex   its position in the plan
     */
    PlannedSession(UUID id, StudyPlan plan, UUID topicId, PlannedSessionKind kind,
            Instant scheduledStart, int durationMinutes, int sequenceIndex) {
        this.id = id;
        this.plan = plan;
        this.topicId = topicId;
        this.kind = kind;
        this.scheduledStart = scheduledStart;
        this.durationMinutes = durationMinutes;
        this.sequenceIndex = sequenceIndex;
    }

    /** Identifier of the planned session. */
    public UUID id() {
        return id;
    }

    /** Plan it belongs to. */
    public StudyPlan plan() {
        return plan;
    }

    /** Topic to be studied. */
    public UUID topicId() {
        return topicId;
    }

    /** New ground or going back over it. */
    public PlannedSessionKind kind() {
        return kind;
    }

    /** When the core placed it. */
    public Instant scheduledStart() {
        return scheduledStart;
    }

    /** How long the plan allows for it. */
    public int durationMinutes() {
        return durationMinutes;
    }

    /** Its position in the plan, unique within the plan. */
    public int sequenceIndex() {
        return sequenceIndex;
    }
}
