package br.com.sinapse.platform.educational.internal.persistence;

import br.com.sinapse.platform.educational.internal.domain.Classroom;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Classrooms. */
public interface ClassroomRepository extends JpaRepository<Classroom, UUID> {

    /**
     * The classrooms of a teacher, newest first.
     *
     * @param teacherId teacher
     * @return their classrooms, archived ones included
     */
    List<Classroom> findByTeacherIdOrderByCreatedAtDesc(UUID teacherId);

    /**
     * Classrooms by identifier, in one query.
     *
     * @param classroomIds classrooms
     * @return the ones that exist
     */
    List<Classroom> findByIdIn(Collection<UUID> classroomIds);
}
