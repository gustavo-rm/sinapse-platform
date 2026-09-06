package br.com.sinapse.platform.educational.internal.service;

import br.com.sinapse.platform.educational.api.ClassroomView;
import br.com.sinapse.platform.educational.api.EnrollmentEndReason;
import br.com.sinapse.platform.educational.internal.domain.Classroom;
import br.com.sinapse.platform.educational.internal.domain.Enrollment;
import br.com.sinapse.platform.educational.internal.domain.Invite;
import br.com.sinapse.platform.educational.internal.domain.Teacher;
import br.com.sinapse.platform.educational.internal.error.NotTheClassroomOwnerException;
import br.com.sinapse.platform.educational.internal.error.UnknownEnrollmentException;
import br.com.sinapse.platform.educational.internal.persistence.ClassroomRepository;
import br.com.sinapse.platform.educational.internal.persistence.EnrollmentRepository;
import br.com.sinapse.platform.educational.internal.persistence.InviteRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opening and closing classrooms, and ending memberships of them.
 *
 * <p>Archiving is where the transaction boundary earns its keep. It has to end every active
 * enrollment and revoke every outstanding invite at once: a classroom that is closed while its
 * invites still work admits students to something that has ended, and one whose enrollments
 * survive leaves teacher access standing on a classroom nobody is running.
 */
@Service
public class ClassroomService {

    private final ClassroomRepository classrooms;
    private final EnrollmentRepository enrollments;
    private final InviteRepository invites;
    private final TeacherDirectory teachers;
    private final Clock clock;

    /**
     * @param classrooms  classrooms
     * @param enrollments enrollments
     * @param invites     invites
     * @param teachers    teacher records
     * @param clock       application clock
     */
    public ClassroomService(ClassroomRepository classrooms, EnrollmentRepository enrollments,
            InviteRepository invites, TeacherDirectory teachers, Clock clock) {
        this.classrooms = classrooms;
        this.enrollments = enrollments;
        this.invites = invites;
        this.teachers = teachers;
        this.clock = clock;
    }

    /**
     * Opens a classroom.
     *
     * @param teacherAccountId account of the teacher opening it
     * @param name             display name
     * @param subjectIds       subjects that carry an institutional deadline here
     * @return the classroom
     */
    @Transactional
    public ClassroomView open(UUID teacherAccountId, String name, Set<UUID> subjectIds) {
        Teacher teacher = teachers.require(teacherAccountId);
        Classroom classroom = classrooms.save(new Classroom(UUID.randomUUID(), teacher.id(), name,
                subjectIds, clock.instant()));
        return EducationalViews.of(classroom);
    }

    /**
     * Closes a classroom, ends its enrollments and revokes its invites.
     *
     * <p>All three in one transaction. Doing the archiving and then the rest would leave a
     * window in which the classroom is closed and its codes still admit people, and a window is
     * all a redemption needs.
     *
     * @param teacherAccountId account of the teacher, who must own it
     * @param classroomId      classroom to archive
     * @return what it did
     */
    @Transactional
    public ArchiveResult archive(UUID teacherAccountId, UUID classroomId) {
        Classroom classroom = requireOwned(teacherAccountId, classroomId);
        Instant now = clock.instant();

        classroom.archive(now);

        List<Enrollment> active = enrollments
                .findByClassroomIdAndEndedAtIsNullOrderByEnrolledAtAsc(classroomId);
        active.forEach(enrollment -> enrollment.end(now, EnrollmentEndReason.CLASSROOM_ARCHIVED));

        List<Invite> outstanding = invites.findByClassroomIdAndRevokedAtIsNull(classroomId);
        outstanding.forEach(invite -> invite.revoke(now));

        return new ArchiveResult(active.size(), outstanding.size());
    }

    /**
     * The student leaves a classroom.
     *
     * @param accountId    student
     * @param enrollmentId enrollment to end, which must be theirs
     * @throws UnknownEnrollmentException if it is not theirs, or is not active
     */
    @Transactional
    public void leave(UUID accountId, UUID enrollmentId) {
        Enrollment enrollment = enrollments.findById(enrollmentId)
                .filter(candidate -> candidate.accountId().equals(accountId))
                .filter(Enrollment::isActive)
                .orElseThrow(UnknownEnrollmentException::new);
        enrollment.end(clock.instant(), EnrollmentEndReason.STUDENT_LEFT);
    }

    /**
     * The teacher removes a student.
     *
     * @param teacherAccountId account of the teacher, who must own the classroom
     * @param classroomId      classroom
     * @param studentAccountId student to remove
     * @throws UnknownEnrollmentException if the student is not currently in the classroom
     */
    @Transactional
    public void remove(UUID teacherAccountId, UUID classroomId, UUID studentAccountId) {
        requireOwned(teacherAccountId, classroomId);
        Enrollment enrollment = enrollments
                .findByClassroomIdAndAccountIdAndEndedAtIsNull(classroomId, studentAccountId)
                .orElseThrow(UnknownEnrollmentException::new);
        enrollment.end(clock.instant(), EnrollmentEndReason.TEACHER_REMOVED);
    }

    /**
     * The classroom, if this teacher owns it.
     *
     * <p>A classroom belonging to somebody else answers the same way as one that does not
     * exist, so that the route cannot be used to find out which identifiers are real.
     *
     * @param teacherAccountId account of the teacher
     * @param classroomId      classroom
     * @return the classroom
     * @throws NotTheClassroomOwnerException if it is not theirs, or does not exist
     */
    @Transactional(readOnly = true)
    public Classroom requireOwned(UUID teacherAccountId, UUID classroomId) {
        Teacher teacher = teachers.require(teacherAccountId);
        return classrooms.findById(classroomId)
                .filter(classroom -> classroom.teacherId().equals(teacher.id()))
                .orElseThrow(NotTheClassroomOwnerException::new);
    }

    /**
     * What archiving a classroom did.
     *
     * @param enrollmentsEnded active enrollments that were ended
     * @param invitesRevoked   outstanding invites that were revoked
     */
    public record ArchiveResult(int enrollmentsEnded, int invitesRevoked) {
    }
}
