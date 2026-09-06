package br.com.sinapse.platform.educational.internal.service;

import br.com.sinapse.platform.educational.api.ClassroomView;
import br.com.sinapse.platform.educational.api.EducationalDirectory;
import br.com.sinapse.platform.educational.api.EnrollmentView;
import br.com.sinapse.platform.educational.api.TeacherView;
import br.com.sinapse.platform.educational.internal.domain.Teacher;
import br.com.sinapse.platform.educational.internal.persistence.ClassroomRepository;
import br.com.sinapse.platform.educational.internal.persistence.EnrollmentRepository;
import br.com.sinapse.platform.educational.internal.persistence.TeacherRepository;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads of classrooms and enrollments.
 *
 * <p>No authorisation is applied here. That belongs to {@code TeacherAccessPolicy}, and a read
 * that quietly filtered by what the caller may see would be a second place where the access
 * rule lives — which is how two places end up disagreeing.
 */
@Service
public class EducationalDirectoryService implements EducationalDirectory {

    private final ClassroomRepository classrooms;
    private final EnrollmentRepository enrollments;
    private final TeacherRepository teachers;

    /**
     * @param classrooms  classrooms
     * @param enrollments enrollments
     * @param teachers    teacher records
     */
    public EducationalDirectoryService(ClassroomRepository classrooms, EnrollmentRepository enrollments,
            TeacherRepository teachers) {
        this.classrooms = classrooms;
        this.enrollments = enrollments;
        this.teachers = teachers;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ClassroomView> classroom(UUID classroomId) {
        return classrooms.findById(classroomId).map(EducationalViews::of);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClassroomView> classroomsOwnedBy(UUID teacherAccountId) {
        return teachers.findByAccountId(teacherAccountId)
                .map(Teacher::id)
                .map(classrooms::findByTeacherIdOrderByCreatedAtDesc)
                .orElseGet(List::of)
                .stream()
                .map(EducationalViews::of)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EnrollmentView> activeEnrollmentsOf(Collection<UUID> accountIds) {
        if (accountIds.isEmpty()) {
            return List.of();
        }
        return enrollments.findByAccountIdInAndEndedAtIsNull(accountIds).stream()
                .map(EducationalViews::of)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EnrollmentView> activeEnrollmentsIn(UUID classroomId) {
        return enrollments.findByClassroomIdAndEndedAtIsNullOrderByEnrolledAtAsc(classroomId).stream()
                .map(EducationalViews::of)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EnrollmentView> enrollmentsOf(UUID accountId) {
        return enrollments.findByAccountIdOrderByEnrolledAtDesc(accountId).stream()
                .map(EducationalViews::of)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, ClassroomView> classroomsByIds(Collection<UUID> classroomIds) {
        if (classroomIds.isEmpty()) {
            return Map.of();
        }
        return classrooms.findAllById(classroomIds).stream()
                .map(EducationalViews::of)
                .collect(Collectors.toMap(ClassroomView::id, view -> view,
                        (first, second) -> first, LinkedHashMap::new));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, TeacherView> teachersByIds(Collection<UUID> teacherIds) {
        if (teacherIds.isEmpty()) {
            return Map.of();
        }
        return teachers.findAllById(teacherIds).stream()
                .map(EducationalViews::of)
                .collect(Collectors.toMap(TeacherView::id, view -> view,
                        (first, second) -> first, LinkedHashMap::new));
    }
}
