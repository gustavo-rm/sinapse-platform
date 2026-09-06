package br.com.sinapse.platform.educational.internal.service;

import br.com.sinapse.platform.educational.api.ClassroomView;
import br.com.sinapse.platform.educational.api.EnrollmentView;
import br.com.sinapse.platform.educational.internal.domain.Classroom;
import br.com.sinapse.platform.educational.internal.domain.Enrollment;

/**
 * The one place an entity becomes a DTO.
 *
 * <p>Kept in one place so that nothing published from this module is a managed entity by
 * accident: a caller handed one can navigate wherever the mapping allows and write to it, at
 * which point the boundary is decoration.
 */
public final class EducationalViews {

    private EducationalViews() {
    }

    /**
     * @param classroom entity
     * @return its published form
     */
    public static ClassroomView of(Classroom classroom) {
        return new ClassroomView(classroom.id(), classroom.teacherId(), classroom.name(),
                classroom.status(), classroom.subjectIds(), classroom.createdAt(),
                classroom.archivedAt());
    }

    /**
     * @param enrollment entity
     * @return its published form
     */
    public static EnrollmentView of(Enrollment enrollment) {
        return new EnrollmentView(enrollment.id(), enrollment.classroomId(), enrollment.accountId(),
                enrollment.enrolledAt(), enrollment.endedAt(), enrollment.endedReason());
    }
}
