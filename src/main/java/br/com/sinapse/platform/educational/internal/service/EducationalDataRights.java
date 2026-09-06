package br.com.sinapse.platform.educational.internal.service;

import br.com.sinapse.platform.educational.api.ClassroomStatus;
import br.com.sinapse.platform.educational.api.ClassroomView;
import br.com.sinapse.platform.educational.api.EducationalDirectory;
import br.com.sinapse.platform.educational.api.EnrollmentView;
import br.com.sinapse.platform.educational.internal.domain.Teacher;
import br.com.sinapse.platform.educational.internal.persistence.EnrollmentRepository;
import br.com.sinapse.platform.educational.internal.persistence.TeacherRepository;
import br.com.sinapse.platform.shared.datarights.ModuleDataRights;
import br.com.sinapse.platform.shared.datarights.ModuleExport;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * What this module owes the holder of an account.
 *
 * <p><strong>Nothing here is deleted, and that is the point.</strong> An enrollment is the
 * record of the access a teacher had to a student's data, and ADR 0011 keeps ended enrollments
 * for exactly that reason: erasing them would remove somebody's account of who could see them.
 * A student being erased therefore leaves this module untouched — their enrollments end when
 * the account is suspended and survive pointing at the anonymised shell.
 *
 * <p><strong>A teacher is a different matter, and the ADR does not cover it.</strong> Its table
 * lists nothing from this module, but {@code teacher} holds a display name and an institution,
 * which are the holder's own personal data. The row cannot be deleted — classrooms reference it
 * and enrollments reference those — so it is emptied the way the account shell is, and the
 * teacher's open classrooms are archived. Leaving a classroom open in the name of somebody who
 * asked to be erased would keep admitting students to a teacher who is not there.
 */
@Component
@Order(EducationalDataRights.ORDER)
public class EducationalDataRights implements ModuleDataRights {

    /** After planning and before identity. Nothing here is referenced by another module. */
    public static final int ORDER = 30;

    private static final Logger LOG = LoggerFactory.getLogger(EducationalDataRights.class);

    private final TeacherRepository teachers;
    private final EnrollmentRepository enrollments;
    private final ClassroomService classrooms;
    private final EducationalDirectory directory;

    /**
     * @param teachers    teacher records
     * @param enrollments memberships
     * @param classrooms  classroom use cases, for the archiving
     * @param directory   reads of this module
     */
    public EducationalDataRights(TeacherRepository teachers, EnrollmentRepository enrollments,
            ClassroomService classrooms, EducationalDirectory directory) {
        this.teachers = teachers;
        this.enrollments = enrollments;
        this.classrooms = classrooms;
        this.directory = directory;
    }

    @Override
    public String moduleName() {
        return "educational";
    }

    @Override
    @Transactional
    public void eraseFor(UUID accountId) {
        int ended = endMemberships(accountId);
        int archived = archiveClassrooms(accountId);
        teachers.findByAccountId(accountId).ifPresent(Teacher::anonymize);

        LOG.info("Erasure ended {} memberships and archived {} classrooms", ended, archived);
    }

    /**
     * Ends the memberships that are still open, keeping every one of them.
     *
     * <p>ADR 0011 says an <em>ended</em> enrollment survives, which leaves the open ones to be
     * dealt with: an anonymised shell cannot be an active member of anything, and a teacher's
     * roster showing somebody who is no longer there would be wrong in the one place this
     * module exists to be right about.
     *
     * <p>The reason recorded is that the student left, because that is what happened. There is a
     * {@code CONSENT_REVOKED} value, and it is not this: withdrawing a consent deliberately
     * leaves an enrollment alone so that re-consenting restores access, and nothing is being
     * restored here.
     */
    private int endMemberships(UUID accountId) {
        int ended = 0;
        for (EnrollmentView membership : directory.enrollmentsOf(accountId)) {
            if (membership.isActive()) {
                classrooms.leave(accountId, membership.id());
                ended++;
            }
        }
        return ended;
    }

    /**
     * Archives the classrooms of a teacher being erased.
     *
     * <p>An open classroom in the name of somebody who asked to be erased would go on admitting
     * students to a teacher who is not there. Archiving ends its memberships and revokes its
     * outstanding invites, which is the same thing the teacher would have done themselves.
     */
    private int archiveClassrooms(UUID accountId) {
        int archived = 0;
        for (ClassroomView classroom : directory.classroomsOwnedBy(accountId)) {
            if (classroom.status() == ClassroomStatus.OPEN) {
                classrooms.archive(accountId, classroom.id());
                archived++;
            }
        }
        return archived;
    }

    @Override
    @Transactional(readOnly = true)
    public ModuleExport exportFor(UUID accountId) {
        Map<String, List<Object>> collections = new LinkedHashMap<>();
        collections.put("enrollments", enrollments
                .findByAccountIdOrderByEnrolledAtDesc(accountId).stream()
                .map(enrollment -> (Object) EducationalViews.of(enrollment))
                .toList());
        collections.put("classroomsOwned", List.copyOf(directory.classroomsOwnedBy(accountId)));
        return new ModuleExport(moduleName(), collections);
    }
}
