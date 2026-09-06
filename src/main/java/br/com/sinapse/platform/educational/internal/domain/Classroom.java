package br.com.sinapse.platform.educational.internal.domain;

import br.com.sinapse.platform.educational.api.ClassroomStatus;
import br.com.sinapse.platform.educational.internal.error.ClassroomAlreadyArchivedException;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A classroom owned by a teacher.
 *
 * <p>The set of subjects is an element collection rather than a root of its own: it is small,
 * bounded, always read with the classroom, and meaningless apart from it.
 *
 * <p>Archiving is the only way a classroom ends, and it is not a deletion. The enrollments it
 * ends stay in the table, because they are what justifies the access a teacher had while it
 * was open — deleting them would erase the answer to "why was this teacher allowed to read
 * that".
 */
@Entity
@Table(name = "classroom")
public class Classroom {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "teacher_id", nullable = false, updatable = false)
    private UUID teacherId;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ClassroomStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    /**
     * Subjects that carry an institutional deadline here.
     *
     * <p>Not a statement about visibility. What the teacher may see is decided by
     * {@code TeacherAccessPolicy}, and today that is everything the student has.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "classroom_subject", joinColumns = @JoinColumn(name = "classroom_id"))
    @Column(name = "subject_id")
    private Set<UUID> subjectIds = new LinkedHashSet<>();

    /** For JPA. */
    protected Classroom() {
    }

    /**
     * Opens a classroom.
     *
     * @param id         identifier
     * @param teacherId  teacher who owns it
     * @param name       display name
     * @param subjectIds subjects carrying an institutional deadline
     * @param createdAt  instant it was opened
     */
    public Classroom(UUID id, UUID teacherId, String name, Set<UUID> subjectIds, Instant createdAt) {
        this.id = id;
        this.teacherId = teacherId;
        this.name = name;
        this.status = ClassroomStatus.OPEN;
        this.createdAt = createdAt;
        this.subjectIds.addAll(subjectIds);
    }

    /** Identifier of the classroom. */
    public UUID id() {
        return id;
    }

    /** Teacher who owns it. */
    public UUID teacherId() {
        return teacherId;
    }

    /** Display name. */
    public String name() {
        return name;
    }

    /** Whether it is still running. */
    public ClassroomStatus status() {
        return status;
    }

    /** Instant it was opened. */
    public Instant createdAt() {
        return createdAt;
    }

    /** Instant it was archived, or {@code null}. */
    public Instant archivedAt() {
        return archivedAt;
    }

    /** Subjects that carry an institutional deadline here. */
    public Set<UUID> subjectIds() {
        return Collections.unmodifiableSet(subjectIds);
    }

    /** Whether the classroom is still accepting redemptions. */
    public boolean isOpen() {
        return status == ClassroomStatus.OPEN;
    }

    /**
     * Archives the classroom.
     *
     * <p>Ending the enrollments and revoking the invites is <em>not</em> done here. Both are
     * other aggregates, and doing it from inside this one would either load them all or leave
     * the caller to remember — which is exactly the kind of "remember to" that stops happening.
     * The service that owns the transaction does all three.
     *
     * @param now instant of the archiving
     * @throws ClassroomAlreadyArchivedException if it is already archived, because a second
     *                                           archiving would move the timestamp and lose
     *                                           when access actually ended
     */
    public void archive(Instant now) {
        if (status == ClassroomStatus.ARCHIVED) {
            throw new ClassroomAlreadyArchivedException();
        }
        this.status = ClassroomStatus.ARCHIVED;
        this.archivedAt = now;
    }
}
