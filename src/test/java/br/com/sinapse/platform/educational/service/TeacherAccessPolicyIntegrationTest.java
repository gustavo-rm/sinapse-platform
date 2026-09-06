package br.com.sinapse.platform.educational.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.sinapse.platform.educational.api.ClassroomView;
import br.com.sinapse.platform.educational.api.EnrollmentView;
import br.com.sinapse.platform.educational.api.TeacherAccessPolicy;
import br.com.sinapse.platform.educational.api.VisibilityScope;
import br.com.sinapse.platform.educational.support.EducationalIntegrationTest;
import br.com.sinapse.platform.identity.api.ConsentPurpose;
import br.com.sinapse.platform.identity.internal.domain.Account;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The derived authorisation of ADR 0005.
 *
 * <p>Two conditions, both read at the moment of the question. The pair of tests that matter
 * most are the ones that take each away separately: ending the enrollment, and withdrawing the
 * consent without touching the enrollment. The second is the one the design was chosen for.
 */
class TeacherAccessPolicyIntegrationTest extends EducationalIntegrationTest {

    @Autowired
    private TeacherAccessPolicy policy;

    @Test
    void aTeacherSeesAStudentEnrolledInTheirClassroomWhoHasConsented() {
        Account teacher = teacher("Prof. Exemplo");
        Account student = student();
        invites.redeem(student.id(), invite(teacher, classroom(teacher)).code());

        assertThat(policy.canViewStudent(teacher.id(), student.id())).isTrue();
        assertThat(policy.scopeFor(teacher.id(), student.id()))
                .as("integral scope: the plan the engine produces allocates across every "
                        + "subject at once, and a teacher who saw only their slice could not "
                        + "read why the student did not study it")
                .isEqualTo(VisibilityScope.ALL);
    }

    @Test
    void aTeacherSeesNothingOfAStudentWhoIsNotTheirs() {
        Account teacher = teacher("Prof. Exemplo");
        Account other = teacher("Prof. Alheio");
        Account student = student();
        invites.redeem(student.id(), invite(teacher, classroom(teacher)).code());

        assertThat(policy.canViewStudent(other.id(), student.id()))
                .as("there is no teacher-to-student relation to fall back on; access is derived "
                        + "from an enrollment or it does not exist")
                .isFalse();
        assertThat(policy.scopeFor(other.id(), student.id()))
                .isEqualTo(new VisibilityScope.Subjects(Set.of()));
    }

    @Test
    void accessStopsWhenTheStudentLeaves() {
        Account teacher = teacher("Prof. Exemplo");
        Account student = student();
        EnrollmentView enrollment = invites.redeem(student.id(),
                invite(teacher, classroom(teacher)).code());
        assertThat(policy.canViewStudent(teacher.id(), student.id())).isTrue();

        classrooms.leave(student.id(), enrollment.id());

        assertThat(policy.canViewStudent(teacher.id(), student.id())).isFalse();
    }

    @Test
    void accessStopsWhenTheTeacherRemovesTheStudent() {
        Account teacher = teacher("Prof. Exemplo");
        ClassroomView classroom = classroom(teacher);
        Account student = student();
        invites.redeem(student.id(), invite(teacher, classroom).code());

        classrooms.remove(teacher.id(), classroom.id(), student.id());

        assertThat(policy.canViewStudent(teacher.id(), student.id())).isFalse();
    }

    @Test
    void accessStopsWhenTheClassroomIsArchived() {
        Account teacher = teacher("Prof. Exemplo");
        ClassroomView classroom = classroom(teacher);
        Account student = student();
        invites.redeem(student.id(), invite(teacher, classroom).code());

        classrooms.archive(teacher.id(), classroom.id());

        assertThat(policy.canViewStudent(teacher.id(), student.id())).isFalse();
    }

    /**
     * The test the whole design rests on.
     *
     * <p>ADR 0005 considered propagating a revocation event from identity and ending the
     * affected enrollments, and rejected it partly because re-consenting would not bring the
     * enrollment back. Checking synchronously means the access goes and returns while the
     * enrollment is never touched, which is what this asserts on both sides.
     */
    @Test
    void withdrawingSharingConsentCutsAccessWithoutTouchingTheEnrollmentAndReconsentingRestoresIt() {
        Account teacher = teacher("Prof. Exemplo");
        Account student = student();
        EnrollmentView enrollment = invites.redeem(student.id(),
                invite(teacher, classroom(teacher)).code());
        assertThat(policy.canViewStudent(teacher.id(), student.id())).isTrue();

        consents.revoke(student.id(), ConsentPurpose.INSTITUTION_SHARING);

        assertThat(policy.canViewStudent(teacher.id(), student.id()))
                .as("no event propagates; the next question simply gets a different answer")
                .isFalse();
        assertThat(jdbc.queryForObject("select ended_at is null from enrollment where id = ?",
                Boolean.class, enrollment.id()))
                .as("the enrollment is untouched, which is what makes the restoration possible")
                .isTrue();

        consents.grant(student.id(), ConsentPurpose.INSTITUTION_SHARING,
                identity.currentTermsId(ConsentPurpose.INSTITUTION_SHARING), identity.evidence());

        assertThat(policy.canViewStudent(teacher.id(), student.id()))
                .as("re-consenting restores the access, with nothing to reconcile")
                .isTrue();
    }

    @Test
    void accessStopsWhenTheStudentIsSuspended() {
        Account teacher = teacher("Prof. Exemplo");
        Account student = student();
        invites.redeem(student.id(), invite(teacher, classroom(teacher)).code());

        consents.revoke(student.id(), ConsentPurpose.LEARNING_DATA_PROCESSING);

        assertThat(policy.canViewStudent(teacher.id(), student.id()))
                .as("the identity gate answers on the status as well as the consent, and this "
                        + "module asks it one question rather than reproducing either")
                .isFalse();
    }

    @Test
    void unknownAccountsSeeNothing() {
        assertThat(policy.canViewStudent(UUID.randomUUID(), UUID.randomUUID())).isFalse();
        assertThat(policy.canViewStudent(null, null)).isFalse();
    }
}
