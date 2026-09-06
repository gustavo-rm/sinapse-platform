package br.com.sinapse.platform.educational.api;

import java.util.Collection;
import java.util.List;
import java.util.Map;
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

    /**
     * Every enrollment of a student, active and ended, newest first.
     *
     * @param accountId student
     * @return their whole history of memberships
     */
    List<EnrollmentView> enrollmentsOf(UUID accountId);

    /**
     * Classrooms by identifier.
     *
     * @param classroomIds classrooms to read
     * @return the ones that exist, keyed by identifier
     */
    Map<UUID, ClassroomView> classroomsByIds(Collection<UUID> classroomIds);

    /**
     * Teachers by identifier.
     *
     * <p>A set rather than one at a time, per rule R7: the caller that needs this is putting a
     * name next to each of a student's memberships, and asking per row would issue one query
     * per classroom.
     *
     * @param teacherIds teachers to read
     * @return the ones that exist, keyed by identifier
     */
    Map<UUID, TeacherView> teachersByIds(Collection<UUID> teacherIds);
}
