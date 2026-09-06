package br.com.sinapse.platform.educational.internal.domain;

import br.com.sinapse.platform.educational.api.EnrollmentEndReason;
import br.com.sinapse.platform.educational.internal.error.EnrollmentAlreadyEndedException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A student's membership of a classroom.
 *
 * <p>A root of its own because every authorisation check reads it, and loading a whole
 * classroom on each of those would be a design error.
 *
 * <p><strong>Never deleted.</strong> Ending an enrollment means writing a timestamp and a
 * reason, and a trigger in the database refuses a delete outright. The point is not tidiness:
 * this record is what justifies the access a teacher had to a student's data while it was
 * active, and a deleted row would leave that access unexplained. It is the same rule consent
 * records follow, for the same reason.
 *
 * <p>Both the account and the classroom are held as identifiers. The account is another
 * module's, so it could not be an association; the classroom could be, and is not, because
 * this entity is read on its own far more often than with its classroom.
 */
@Entity
@Table(name = "enrollment")
public class Enrollment {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "classroom_id", nullable = false, updatable = false)
    private UUID classroomId;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    /** The invite this came from, when it came from one. */
    @Column(name = "invite_id", updatable = false)
    private UUID inviteId;

    @Column(name = "enrolled_at", nullable = false, updatable = false)
    private Instant enrolledAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "ended_reason")
    private EnrollmentEndReason endedReason;

    /** For JPA. */
    protected Enrollment() {
    }

    /**
     * Enrolls a student.
     *
     * @param id          identifier
     * @param classroomId classroom
     * @param accountId   student
     * @param inviteId    invite redeemed, or {@code null}
     * @param enrolledAt  instant of the enrollment
     */
    public Enrollment(UUID id, UUID classroomId, UUID accountId, UUID inviteId, Instant enrolledAt) {
        this.id = id;
        this.classroomId = classroomId;
        this.accountId = accountId;
        this.inviteId = inviteId;
        this.enrolledAt = enrolledAt;
    }

    /** Identifier of the enrollment. */
    public UUID id() {
        return id;
    }

    /** Classroom the student is in. */
    public UUID classroomId() {
        return classroomId;
    }

    /** Student. */
    public UUID accountId() {
        return accountId;
    }

    /** Invite redeemed, when there was one. */
    public UUID inviteId() {
        return inviteId;
    }

    /** Instant the enrollment started. */
    public Instant enrolledAt() {
        return enrolledAt;
    }

    /** Instant it ended, or {@code null}. */
    public Instant endedAt() {
        return endedAt;
    }

    /** Why it ended, or {@code null}. */
    public EnrollmentEndReason endedReason() {
        return endedReason;
    }

    /** Whether the student is currently in the classroom. */
    public boolean isActive() {
        return endedAt == null;
    }

    /**
     * Ends the enrollment.
     *
     * @param at     instant it ended
     * @param reason why
     * @throws EnrollmentAlreadyEndedException if it has already ended, because moving the
     *                                         timestamp would rewrite when the teacher's
     *                                         access actually stopped
     */
    public void end(Instant at, EnrollmentEndReason reason) {
        if (endedAt != null) {
            throw new EnrollmentAlreadyEndedException();
        }
        this.endedAt = at;
        this.endedReason = reason;
    }
}
