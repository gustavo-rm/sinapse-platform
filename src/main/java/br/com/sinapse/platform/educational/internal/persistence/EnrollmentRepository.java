package br.com.sinapse.platform.educational.internal.persistence;

import br.com.sinapse.platform.educational.internal.domain.Enrollment;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Enrollments. Never deleted: nothing here removes a row. */
public interface EnrollmentRepository extends JpaRepository<Enrollment, UUID> {

    /**
     * The active enrollment of a student in a classroom.
     *
     * @param classroomId classroom
     * @param accountId   student
     * @return the enrollment, if the student is currently in it
     */
    Optional<Enrollment> findByClassroomIdAndAccountIdAndEndedAtIsNull(UUID classroomId, UUID accountId);

    /**
     * Who is currently in a classroom, oldest first.
     *
     * @param classroomId classroom
     * @return the active enrollments
     */
    List<Enrollment> findByClassroomIdAndEndedAtIsNullOrderByEnrolledAtAsc(UUID classroomId);

    /**
     * The enrollments of a student, active and ended, newest first.
     *
     * @param accountId student
     * @return their whole history of memberships
     */
    List<Enrollment> findByAccountIdOrderByEnrolledAtDesc(UUID accountId);

    /**
     * The active enrollments of a set of students, in one query.
     *
     * <p>Rule R7. The read models ask about a whole classroom at once.
     *
     * @param accountIds students
     * @return their active enrollments
     */
    List<Enrollment> findByAccountIdInAndEndedAtIsNull(Collection<UUID> accountIds);

    /**
     * Whether a student is currently enrolled in any classroom of a teacher.
     *
     * <p>This is the query behind every teacher read, so it answers with an existence check
     * rather than by loading rows: the caller wants a yes or a no, and the partial index on
     * active enrollments is what makes it cheap.
     *
     * @param teacherAccountId account of the teacher
     * @param studentAccountId account of the student
     * @return whether such an enrollment exists
     */
    @Query("""
            select count(enrollment) > 0
              from Enrollment enrollment, Classroom classroom, Teacher teacher
             where enrollment.classroomId = classroom.id
               and classroom.teacherId = teacher.id
               and teacher.accountId = :teacherAccountId
               and enrollment.accountId = :studentAccountId
               and enrollment.endedAt is null
            """)
    boolean existsActiveEnrollmentUnderTeacher(@Param("teacherAccountId") UUID teacherAccountId,
            @Param("studentAccountId") UUID studentAccountId);

    /**
     * Which of a set of students are currently enrolled in a classroom of a teacher.
     *
     * <p>The batch form of {@link #existsActiveEnrollmentUnderTeacher}, for the class list.
     * Identifiers and not rows: the caller wants a yes or a no per student, and a student in
     * two of that teacher's classrooms would otherwise come back twice.
     *
     * @param teacherAccountId  account of the teacher
     * @param studentAccountIds students to test
     * @return the identifiers of those with an active enrollment under that teacher
     */
    @Query("""
            select distinct enrollment.accountId
              from Enrollment enrollment, Classroom classroom, Teacher teacher
             where enrollment.classroomId = classroom.id
               and classroom.teacherId = teacher.id
               and teacher.accountId = :teacherAccountId
               and enrollment.accountId in :studentAccountIds
               and enrollment.endedAt is null
            """)
    Set<UUID> findStudentsEnrolledUnderTeacher(@Param("teacherAccountId") UUID teacherAccountId,
            @Param("studentAccountIds") Collection<UUID> studentAccountIds);
}
