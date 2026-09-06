package br.com.sinapse.platform.readmodel.internal.service;

import br.com.sinapse.platform.educational.api.ClassroomView;
import br.com.sinapse.platform.educational.api.EducationalDirectory;
import br.com.sinapse.platform.educational.api.TeacherAccessPolicy;
import br.com.sinapse.platform.identity.api.AccountAccessPolicy;
import br.com.sinapse.platform.readmodel.internal.error.LearningDataNotReadableException;
import br.com.sinapse.platform.readmodel.internal.error.NotReadableException;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The one place these reads ask whether they may look at a student.
 *
 * <p>Section 10 of {@code CLAUDE.md}: every read of student data passes through
 * {@code AccountAccessPolicy} or {@code TeacherAccessPolicy}, and no service scatters status
 * checks of its own. This component holds neither rule — it asks the two modules that own them
 * and translates the answer into the failure the edge reports.
 *
 * <p>Two distinct questions, and they are not variants of one another. A holder reading their
 * own data has to be processable; a teacher reading somebody else's has to own the classroom,
 * have the student actively enrolled in it, and have that student's sharing consent in force at
 * this instant. The second is asked afresh on every request, which is what makes a withdrawal
 * take effect immediately rather than propagate.
 */
@Component
public class ReadModelAccess {

    private final AccountAccessPolicy accounts;
    private final TeacherAccessPolicy teachers;
    private final EducationalDirectory classrooms;

    /**
     * @param accounts   the access gate of the identity module
     * @param teachers   the derived teacher-to-student authorisation
     * @param classrooms classrooms, read to establish ownership
     */
    public ReadModelAccess(AccountAccessPolicy accounts, TeacherAccessPolicy teachers,
            EducationalDirectory classrooms) {
        this.accounts = accounts;
        this.teachers = teachers;
        this.classrooms = classrooms;
    }

    /**
     * Refuses unless the holder's own learning data may be processed.
     *
     * @param accountId the caller, reading themselves
     * @throws LearningDataNotReadableException if it may not
     */
    public void requireOwnLearningData(UUID accountId) {
        if (!accounts.canProcessLearningData(accountId)) {
            throw new LearningDataNotReadableException();
        }
    }

    /**
     * Whether the holder's own learning data may be processed.
     *
     * <p>Asked rather than required by the one read that has to answer a holder it may not
     * read: the initial screen is how a suspended holder finds out they are suspended.
     *
     * @param accountId the caller
     * @return whether the learning half of that screen may be filled in
     */
    public boolean mayReadOwnLearningData(UUID accountId) {
        return accounts.canProcessLearningData(accountId);
    }

    /**
     * The classroom, provided the caller owns it.
     *
     * @param teacherAccountId caller
     * @param classroomId      classroom
     * @return the classroom
     * @throws NotReadableException if it is not theirs, or does not exist
     */
    public ClassroomView requireOwnedClassroom(UUID teacherAccountId, UUID classroomId) {
        return classrooms.classroomOwnedBy(teacherAccountId, classroomId)
                .orElseThrow(NotReadableException::new);
    }

    /**
     * Refuses unless the teacher may read the student right now.
     *
     * @param teacherAccountId caller
     * @param studentAccountId student being asked about
     * @throws NotReadableException if the enrollment or the consent is not there
     */
    public void requireViewableStudent(UUID teacherAccountId, UUID studentAccountId) {
        if (!teachers.canViewStudent(teacherAccountId, studentAccountId)) {
            throw new NotReadableException();
        }
    }
}
