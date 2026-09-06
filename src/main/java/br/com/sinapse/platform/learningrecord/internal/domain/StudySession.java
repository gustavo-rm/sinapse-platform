package br.com.sinapse.platform.learningrecord.internal.domain;

import br.com.sinapse.platform.learningrecord.api.DurationSource;
import br.com.sinapse.platform.learningrecord.api.RecallRating;
import br.com.sinapse.platform.learningrecord.api.SessionKind;
import br.com.sinapse.platform.learningrecord.api.SessionSource;
import br.com.sinapse.platform.learningrecord.api.SessionStatus;
import br.com.sinapse.platform.learningrecord.internal.error.SessionAlreadyClosedException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * A study session the student actually executed. The single root of this module.
 *
 * <p><strong>One class for both kinds of session.</strong> A session started from the plan
 * and one the student simply decided to do are the same record; {@code source} and the
 * presence of {@code plannedSessionId} are the entire difference. Section 8.1 of the
 * architecture document requires this, and the reason is that the two are the same evidence:
 * a second class would end up with a second set of queries, and off-plan study would slowly
 * become the kind that does not count.
 *
 * <p>{@code source} is derived from {@code plannedSessionId} rather than accepted from the
 * caller, so invariant 5 cannot be broken by an argument in the wrong order. The database
 * checks it too, and a check constraint that no code path can reach is exactly what one
 * wants.
 *
 * <p>{@code plannedSessionId} is a bare {@code UUID} with no foreign key. Rule R2: neither
 * planning nor this module may depend on the other. The accepted cost is a reference to a
 * planned session that no longer exists, reported by a consistency job and never repaired by
 * deleting the evidence.
 *
 * <p><strong>Closed is closed.</strong> Once the session leaves {@code IN_PROGRESS} nothing
 * about it changes. The methods here refuse, and a database trigger refuses underneath them —
 * the trigger is defence against a mapping accident rather than against a caller, in the same
 * spirit as the immutability trigger on consent records. Correcting a closed session is a new
 * record, because evidence that can be edited is not evidence.
 *
 * <p>{@code created_at} is deliberately not mapped. It is the instant the row was written,
 * which the database fills in and the application cannot set; for a retroactive entry it
 * differs from {@code startedAt} by however long the student took to get round to it, and
 * that difference is worth keeping honest.
 */
@Entity
@Table(name = "study_session")
public class StudySession {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "topic_id", nullable = false, updatable = false)
    private UUID topicId;

    /** The planned session this executed, when it came from a plan. No foreign key: rule R2. */
    @Column(name = "planned_session_id", updatable = false)
    private UUID plannedSessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, updatable = false)
    private SessionKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, updatable = false)
    private SessionSource source;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SessionStatus status;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "planned_duration_minutes", updatable = false)
    private Integer plannedDurationMinutes;

    @Column(name = "actual_duration_minutes")
    private Integer actualDurationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "duration_source")
    private DurationSource durationSource;

    @Enumerated(EnumType.STRING)
    @Column(name = "recall_rating")
    private RecallRating recallRating;

    /** For JPA. */
    protected StudySession() {
    }

    private StudySession(UUID id, UUID accountId, UUID topicId, UUID plannedSessionId,
            SessionKind kind, Instant startedAt, Integer plannedDurationMinutes) {
        this.id = id;
        this.accountId = accountId;
        this.topicId = topicId;
        this.plannedSessionId = plannedSessionId;
        this.kind = kind;
        this.source = plannedSessionId == null
                ? SessionSource.SELF_DIRECTED
                : SessionSource.FROM_PLAN;
        this.status = SessionStatus.IN_PROGRESS;
        this.startedAt = startedAt;
        this.plannedDurationMinutes = plannedDurationMinutes;
    }

    /**
     * Starts a session, timed by the application.
     *
     * <p>This is the main path of decision F5. The duration will be measured rather than
     * asked for, which is what makes it {@link DurationSource#MEASURED} when the session
     * closes.
     *
     * @param id                     identifier
     * @param accountId              student
     * @param topicId                topic being studied
     * @param plannedSessionId       planned session being executed, or {@code null} when the
     *                               student is studying on their own initiative
     * @param kind                   new ground or going back over it
     * @param startedAt              instant it started
     * @param plannedDurationMinutes what the plan asked for, or {@code null}
     * @return the running session
     */
    public static StudySession start(UUID id, UUID accountId, UUID topicId, UUID plannedSessionId,
            SessionKind kind, Instant startedAt, Integer plannedDurationMinutes) {
        return new StudySession(id, accountId, topicId, plannedSessionId, kind, startedAt,
                plannedDurationMinutes);
    }

    /**
     * Records a session that already happened and was never timed.
     *
     * <p>The marked exception of decision F5. It is born closed, and its duration is
     * {@link DurationSource#SELF_REPORTED} with no way to claim otherwise — that is the whole
     * point of the field. The record is structurally identical to a timed one; only the
     * source of the number differs, and it differs visibly, so the two sets can be analysed
     * apart and the second discarded if the pilot has to.
     *
     * <p>The end is derived from the start and the duration rather than asked for separately.
     * Two numbers the student supplies independently can disagree — fifty minutes of study
     * inside a half-hour window — and there would be no way to tell which of the two they
     * meant. One start and one length cannot contradict each other.
     *
     * @param id                    identifier
     * @param accountId             student
     * @param topicId               topic studied
     * @param plannedSessionId      planned session executed, or {@code null}
     * @param kind                  new ground or going back over it
     * @param startedAt             instant the student says it started
     * @param actualDurationMinutes how long the student says it took
     * @param recallRating          the student's judgement of their recall
     * @return the closed session
     */
    public static StudySession recordRetroactively(UUID id, UUID accountId, UUID topicId,
            UUID plannedSessionId, SessionKind kind, Instant startedAt,
            int actualDurationMinutes, RecallRating recallRating) {

        StudySession session = new StudySession(id, accountId, topicId, plannedSessionId, kind,
                startedAt, null);
        session.status = SessionStatus.COMPLETED;
        session.endedAt = startedAt.plus(Duration.ofMinutes(actualDurationMinutes));
        session.actualDurationMinutes = actualDurationMinutes;
        session.durationSource = DurationSource.SELF_REPORTED;
        session.recallRating = recallRating;
        return session;
    }

    /** Identifier of the session. */
    public UUID id() {
        return id;
    }

    /** Student who studied. */
    public UUID accountId() {
        return accountId;
    }

    /** Topic studied. */
    public UUID topicId() {
        return topicId;
    }

    /** Planned session executed, or {@code null}. */
    public UUID plannedSessionId() {
        return plannedSessionId;
    }

    /** New ground or going back over it. */
    public SessionKind kind() {
        return kind;
    }

    /** From the plan or self-directed. Derived from the presence of a planned session. */
    public SessionSource source() {
        return source;
    }

    /** Where the session is in its life. */
    public SessionStatus status() {
        return status;
    }

    /** Instant it started. */
    public Instant startedAt() {
        return startedAt;
    }

    /** Instant it closed, or {@code null} while running. */
    public Instant endedAt() {
        return endedAt;
    }

    /** What the plan asked for, or {@code null}. */
    public Integer plannedDurationMinutes() {
        return plannedDurationMinutes;
    }

    /** What it took, or {@code null} while running. */
    public Integer actualDurationMinutes() {
        return actualDurationMinutes;
    }

    /** How the duration was arrived at, present exactly when the duration is. */
    public DurationSource durationSource() {
        return durationSource;
    }

    /** The student's judgement of their recall, only on a completed session. */
    public RecallRating recallRating() {
        return recallRating;
    }

    /** Whether the session is still running. */
    public boolean isOpen() {
        return status == SessionStatus.IN_PROGRESS;
    }

    /**
     * Closes the session as completed, with the duration the application measured.
     *
     * <p>The elapsed time is rounded to the nearest minute rather than truncated. Truncation
     * would shorten every session, and a bias that always points the same way is worse in an
     * evidence store than a rounding error that does not.
     *
     * @param at           instant it closed, which must not precede the start
     * @param recallRating the student's judgement of their recall
     * @throws SessionAlreadyClosedException if the session is not running
     * @throws IllegalArgumentException      if the instant precedes the start
     */
    public void complete(Instant at, RecallRating recallRating) {
        close(at, SessionStatus.COMPLETED);
        this.actualDurationMinutes = minutesBetween(startedAt, at);
        this.durationSource = DurationSource.MEASURED;
        this.recallRating = recallRating;
    }

    /**
     * Closes the session as completed, with a duration the student states.
     *
     * <p>Used when the timer did not reflect what happened — the student left it running, or
     * closed the application. The number is theirs, so the source says so.
     *
     * @param at                    instant it closed, which must not precede the start
     * @param actualDurationMinutes how long the student says it took
     * @param recallRating          the student's judgement of their recall
     * @throws SessionAlreadyClosedException if the session is not running
     * @throws IllegalArgumentException      if the instant precedes the start
     */
    public void completeWithReportedDuration(Instant at, int actualDurationMinutes,
            RecallRating recallRating) {
        close(at, SessionStatus.COMPLETED);
        this.actualDurationMinutes = actualDurationMinutes;
        this.durationSource = DurationSource.SELF_REPORTED;
        this.recallRating = recallRating;
    }

    /**
     * Closes the session as abandoned.
     *
     * <p>No rating, by invariant 4, and no duration either: the student stopped, and the
     * elapsed time of a session somebody walked away from measures nothing. What the record
     * says is that the session was opened and not finished, which is exactly what adherence
     * needs to know.
     *
     * @param at instant it closed, which must not precede the start
     * @throws SessionAlreadyClosedException if the session is not running
     * @throws IllegalArgumentException      if the instant precedes the start
     */
    public void abandon(Instant at) {
        close(at, SessionStatus.ABANDONED);
    }

    private void close(Instant at, SessionStatus closedStatus) {
        if (status.isClosed()) {
            throw new SessionAlreadyClosedException();
        }
        if (at.isBefore(startedAt)) {
            throw new IllegalArgumentException("a session cannot end before it started");
        }
        this.status = closedStatus;
        this.endedAt = at;
    }

    private static int minutesBetween(Instant from, Instant to) {
        return Math.toIntExact(Math.round(Duration.between(from, to).toSeconds() / 60.0));
    }
}
