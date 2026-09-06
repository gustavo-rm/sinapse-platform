package br.com.sinapse.platform.educational.api;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads of classrooms and enrollments, for whatever composes above this module.
 *
 * <p>Every lookup that could be asked about many things takes a set, per architecture rule
 * R7: the read models compose across modules without joining, so a module offering only unit
 * lookups turns a classroom of forty students into forty queries.
 *
 * <p>Nothing here checks authorisation. That is {@link TeacherAccessPolicy}, and keeping the
 * two apart is deliberate — a read that quietly filtered by what the caller may see would be
 * a second place where the access rule lives.
 */
public interface EducationalDirectory {

    /**
     * A classroom by identifier.
     *
     * @param classroomId classroom
     * @return the classroom, if it exists
     */
    Optional<ClassroomView> classroom(UUID classroomId);

    /**
     * The classrooms a teacher owns, newest first.
     *
     * @param teacherAccountId account of the teacher
     * @return their classrooms, archived ones included
     */
    List<ClassroomView> classroomsOwnedBy(UUID teacherAccountId);

    /**
     * The active enrollments of a set of students.
     *
     * @param accountIds students
     * @return their active enrollments
     */
    List<EnrollmentView> activeEnrollmentsOf(Collection<UUID> accountIds);

    /**
     * The active enrollments in a classroom, oldest first.
     *
     * @param classroomId classroom
     * @return who is currently in it
     */
    List<EnrollmentView> activeEnrollmentsIn(UUID classroomId);
}
