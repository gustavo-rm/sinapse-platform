package br.com.sinapse.platform.planning.internal.domain;

import br.com.sinapse.platform.planning.api.GoalStatus;
import br.com.sinapse.platform.planning.internal.error.GoalAlreadyClosedException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A subject the student intends to get through.
 *
 * <p>It names a subject and never a topic. Every topic of the subject is in scope and the
 * optimisation core decides the order and what fits — decision L3. There is no exclusion
 * list, because for the exam preparation this is built for the usual scope is the whole
 * syllabus, and building exclusion now would be a feature invented ahead of anyone wanting it.
 *
 * <p>{@code targetDate} is a prioritisation constraint, not the plan horizon (decision F3).
 * It enters the snapshot as pressure; the horizon stays what configuration says it is.
 *
 * <p>A goal is never deleted. It is achieved or it is abandoned, and either way the instant is
 * written down — a goal a student gave up on in week three is data about what people actually
 * pursue, and deleting it would throw that away to keep a list tidy.
 */
@Entity
@Table(name = "study_goal")
public class StudyGoal {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    /**
     * The subject, held as an identifier.
     *
     * <p>Another module's aggregate, so it could not be an association: rule R1 allows no JPA
     * relationship across a module boundary. The foreign key is allowed and present, because
     * it points the way this module is already permitted to depend (rule R4).
     */
    @Column(name = "subject_id", nullable = false, updatable = false)
    private UUID subjectId;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Column(name = "priority", nullable = false)
    private short priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private GoalStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * When the goal stopped being pursued, whichever way it ended.
     *
     * <p>The column is called {@code achieved_at} and the migration is authoritative, but it
     * is written for an abandonment too. Losing the instant a student gave up would throw away
     * the more interesting half of the data; which of the two happened is {@code status}, and
     * that is the field to read.
     */
    @Column(name = "achieved_at")
    private Instant achievedAt;

    /** For JPA. */
    protected StudyGoal() {
    }

    /**
     * Sets a goal.
     *
     * @param id         identifier
     * @param accountId  student
     * @param subjectId  subject in the curriculum catalogue
     * @param targetDate when the student would like to be done, or {@code null}
     * @param priority   1 to 5, higher meaning more pressing
     * @param createdAt  when it was set
     */
    public StudyGoal(UUID id, UUID accountId, UUID subjectId, LocalDate targetDate, int priority,
            Instant createdAt) {
        this.id = id;
        this.accountId = accountId;
        this.subjectId = subjectId;
        this.targetDate = targetDate;
        this.priority = (short) priority;
        this.status = GoalStatus.ACTIVE;
        this.createdAt = createdAt;
    }

    /** Identifier of the goal. */
    public UUID id() {
        return id;
    }

    /** Student the goal belongs to. */
    public UUID accountId() {
        return accountId;
    }

    /** Subject in scope. Every topic of it is in scope with it. */
    public UUID subjectId() {
        return subjectId;
    }

    /** When the student would like to be done, or {@code null}. */
    public LocalDate targetDate() {
        return targetDate;
    }

    /** 1 to 5, higher meaning more pressing. */
    public int priority() {
        return priority;
    }

    /** Where the goal stands. */
    public GoalStatus status() {
        return status;
    }

    /** When it was set. */
    public Instant createdAt() {
        return createdAt;
    }

    /** When it stopped being pursued, or {@code null} while active. */
    public Instant achievedAt() {
        return achievedAt;
    }

    /** Whether the goal still enters plan generation. */
    public boolean isActive() {
        return status == GoalStatus.ACTIVE;
    }

    /**
     * Changes what the goal asks for.
     *
     * <p>The subject is not among what can change. A goal about another subject is another
     * goal, and letting one be repointed would silently move the history of an abandoned
     * subject onto a new one.
     *
     * @param targetDate new target date, or {@code null} to drop it
     * @param priority   new priority, 1 to 5
     * @throws GoalAlreadyClosedException if the goal has ended
     */
    public void revise(LocalDate targetDate, int priority) {
        requireOpen();
        this.targetDate = targetDate;
        this.priority = (short) priority;
    }

    /**
     * Closes the goal as achieved.
     *
     * @param at instant it was closed
     * @throws GoalAlreadyClosedException if it has already ended
     */
    public void achieve(Instant at) {
        close(GoalStatus.ACHIEVED, at);
    }

    /**
     * Closes the goal as abandoned.
     *
     * @param at instant it was closed
     * @throws GoalAlreadyClosedException if it has already ended
     */
    public void abandon(Instant at) {
        close(GoalStatus.ABANDONED, at);
    }

    private void close(GoalStatus closedStatus, Instant at) {
        requireOpen();
        this.status = closedStatus;
        this.achievedAt = at;
    }

    private void requireOpen() {
        if (status != GoalStatus.ACTIVE) {
            throw new GoalAlreadyClosedException();
        }
    }
}
