package br.com.sinapse.platform.datarights.internal.service;

import br.com.sinapse.platform.datarights.api.TeacherAccessPeriod;
import br.com.sinapse.platform.educational.api.ClassroomView;
import br.com.sinapse.platform.educational.api.EducationalDirectory;
import br.com.sinapse.platform.educational.api.EnrollmentView;
import br.com.sinapse.platform.educational.api.TeacherView;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Who could read the holder's data, and when.
 *
 * <p>Article 18's right to information about sharing. It is derived rather than logged: there is
 * no direct teacher-to-student relation in this platform (rule R6), and the only thing that ever
 * grants a teacher access is an active enrollment in a classroom they own. So the periods are
 * the memberships, and no new table records what the existing ones already say.
 *
 * <p>Read-only, and composed through the educational module's published surface rather than by
 * reading its tables. Two batch lookups and no query per row.
 */
@Service
public class AccessDisclosureService {

    private final EducationalDirectory educational;

    /**
     * @param educational classrooms, memberships and the teachers who own them
     */
    public AccessDisclosureService(EducationalDirectory educational) {
        this.educational = educational;
    }

    /**
     * Every period in which a teacher could read the holder's data.
     *
     * @param accountId holder asking who has seen their data
     * @return the periods, newest membership first
     */
    @Transactional(readOnly = true)
    public List<TeacherAccessPeriod> disclosureFor(UUID accountId) {
        List<EnrollmentView> memberships = educational.enrollmentsOf(accountId);
        if (memberships.isEmpty()) {
            return List.of();
        }

        Set<UUID> classroomIds = memberships.stream()
                .map(EnrollmentView::classroomId)
                .collect(Collectors.toSet());
        Map<UUID, ClassroomView> classrooms = educational.classroomsByIds(classroomIds);
        Map<UUID, TeacherView> teachers = educational.teachersByIds(classrooms.values().stream()
                .map(ClassroomView::teacherId)
                .collect(Collectors.toSet()));

        return memberships.stream()
                .map(membership -> periodOf(membership, classrooms, teachers))
                .toList();
    }

    private static TeacherAccessPeriod periodOf(EnrollmentView membership,
            Map<UUID, ClassroomView> classrooms, Map<UUID, TeacherView> teachers) {

        ClassroomView classroom = classrooms.get(membership.classroomId());
        TeacherView teacher = classroom == null ? null : teachers.get(classroom.teacherId());
        return new TeacherAccessPeriod(
                membership.classroomId(),
                classroom == null ? null : classroom.name(),
                teacher == null ? null : teacher.displayName(),
                teacher == null ? null : teacher.institutionName(),
                membership.enrolledAt(),
                membership.endedAt());
    }
}
