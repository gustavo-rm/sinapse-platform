package br.com.sinapse.platform.educational.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.sinapse.platform.educational.api.ClassroomView;
import br.com.sinapse.platform.educational.api.EnrollmentView;
import br.com.sinapse.platform.educational.internal.domain.Invite;
import br.com.sinapse.platform.educational.internal.error.AlreadyEnrolledException;
import br.com.sinapse.platform.educational.internal.error.InviteNotRedeemableException;
import br.com.sinapse.platform.educational.internal.error.SharingConsentRequiredException;
import br.com.sinapse.platform.educational.internal.error.TeacherCannotEnrollException;
import br.com.sinapse.platform.educational.support.EducationalIntegrationTest;
import br.com.sinapse.platform.identity.api.ConsentPurpose;
import br.com.sinapse.platform.identity.internal.domain.Account;
import java.time.Duration;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * Invariants 1 to 4, each exercised by making it fail.
 *
 * <p>Invariant 1 has four ways to be violated and they all answer identically, which is a rule
 * in its own right: the code is stored in clear, and an answer that told a guesser "that code
 * is real but expired" would give away exactly what the fifty bits exist to withhold.
 */
class InviteRedemptionIntegrationTest extends EducationalIntegrationTest {

    @Test
    void aStudentWithConsentJoinsTheClassroom() {
        Account teacher = teacher("Prof. Exemplo");
        ClassroomView classroom = classroom(teacher);
        Invite invite = invite(teacher, classroom);
        Account student = student();

        EnrollmentView enrollment = invites.redeem(student.id(), invite.code());

        assertThat(enrollment.classroomId()).isEqualTo(classroom.id());
        assertThat(enrollment.accountId()).isEqualTo(student.id());
        assertThat(enrollment.isActive()).isTrue();
        assertThat(jdbc.queryForObject("select use_count from invite where id = ?",
                Integer.class, invite.id())).isEqualTo(1);
    }

    @Test
    void anExpiredInviteIsRejected() {
        Account teacher = teacher("Prof. Exemplo");
        Invite invite = invite(teacher, classroom(teacher), Duration.ofDays(1), null);
        Account student = student();

        // Both columns move: ck_invite_expiry refuses an invite that expired before it was
        // created, so an invite in the past has to have been issued further in the past.
        jdbc.update("update invite set created_at = now() - interval '2 days', "
                + "expires_at = now() - interval '1 minute' where id = ?", invite.id());

        assertThatThrownBy(() -> invites.redeem(student.id(), invite.code()))
                .isInstanceOf(InviteNotRedeemableException.class);
        assertThat(activeEnrollmentCount(invite.classroomId())).isZero();
    }

    @Test
    void aRevokedInviteIsRejected() {
        Account teacher = teacher("Prof. Exemplo");
        Invite invite = invite(teacher, classroom(teacher));
        Account student = student();

        invites.revoke(teacher.id(), invite.id());

        assertThatThrownBy(() -> invites.redeem(student.id(), invite.code()))
                .isInstanceOf(InviteNotRedeemableException.class);
    }

    @Test
    void anExhaustedInviteIsRejected() {
        Account teacher = teacher("Prof. Exemplo");
        ClassroomView classroom = classroom(teacher);
        Invite invite = invite(teacher, classroom, Duration.ofDays(7), 1);

        invites.redeem(student().id(), invite.code());
        Account second = student();

        assertThatThrownBy(() -> invites.redeem(second.id(), invite.code()))
                .isInstanceOf(InviteNotRedeemableException.class);
        assertThat(activeEnrollmentCount(classroom.id())).isEqualTo(1);
    }

    @Test
    void anInviteOfAnArchivedClassroomIsRejected() {
        Account teacher = teacher("Prof. Exemplo");
        ClassroomView classroom = classroom(teacher);
        Invite invite = invite(teacher, classroom);
        Account student = student();

        classrooms.archive(teacher.id(), classroom.id());

        assertThatThrownBy(() -> invites.redeem(student.id(), invite.code()))
                .as("a code that admits to a classroom nobody is running admits to nothing")
                .isInstanceOf(InviteNotRedeemableException.class);
    }

    @Test
    void anUnknownCodeIsRejectedTheSameWayAsAnExpiredOne() {
        Account student = student();

        assertThatThrownBy(() -> invites.redeem(student.id(), "ZZZZZZZZZZ"))
                .isInstanceOf(InviteNotRedeemableException.class);
    }

    @Test
    void aStudentWithoutSharingConsentIsRejected() {
        Account teacher = teacher("Prof. Exemplo");
        Invite invite = invite(teacher, classroom(teacher));
        Account student = studentWithoutSharingConsent();

        assertThatThrownBy(() -> invites.redeem(student.id(), invite.code()))
                .as("invariant 2: joining a classroom is the sharing, so the consent has to be "
                        + "in force before it happens")
                .isInstanceOf(SharingConsentRequiredException.class);
        assertThat(activeEnrollmentCount(invite.classroomId())).isZero();
    }

    @Test
    void aStudentWhoWithdrewSharingConsentIsRejected() {
        Account teacher = teacher("Prof. Exemplo");
        Invite invite = invite(teacher, classroom(teacher));
        Account student = student();
        consents.revoke(student.id(), ConsentPurpose.INSTITUTION_SHARING);

        assertThatThrownBy(() -> invites.redeem(student.id(), invite.code()))
                .isInstanceOf(SharingConsentRequiredException.class);
    }

    @Test
    void aSuspendedStudentIsRejected() {
        Account teacher = teacher("Prof. Exemplo");
        Invite invite = invite(teacher, classroom(teacher));
        Account student = student();
        // Withdrawing the essential purpose suspends the account, which is the other half of
        // invariant 2: the gate answers on both the status and the consent.
        consents.revoke(student.id(), ConsentPurpose.LEARNING_DATA_PROCESSING);

        assertThatThrownBy(() -> invites.redeem(student.id(), invite.code()))
                .isInstanceOf(SharingConsentRequiredException.class);
    }

    @Test
    void aTeacherCannotJoinTheirOwnClassroom() {
        Account teacher = teacher("Prof. Exemplo");
        ClassroomView classroom = classroom(teacher);
        Invite invite = invite(teacher, classroom);
        consents.grant(teacher.id(), ConsentPurpose.INSTITUTION_SHARING,
                identity.currentTermsId(ConsentPurpose.INSTITUTION_SHARING), identity.evidence());

        assertThatThrownBy(() -> invites.redeem(teacher.id(), invite.code()))
                .as("invariant 4, and the only one the database does not check: it spans two "
                        + "aggregates, so this test is the whole of its enforcement")
                .isInstanceOf(TeacherCannotEnrollException.class);
        assertThat(activeEnrollmentCount(classroom.id())).isZero();
    }

    @Test
    void aTeacherMayJoinSomebodyElsesClassroom() {
        Account owner = teacher("Prof. Dono");
        Account other = teacher("Prof. Visitante");
        Invite invite = invite(owner, classroom(owner));
        consents.grant(other.id(), ConsentPurpose.INSTITUTION_SHARING,
                identity.currentTermsId(ConsentPurpose.INSTITUTION_SHARING), identity.evidence());

        EnrollmentView enrollment = invites.redeem(other.id(), invite.code());

        assertThat(enrollment.isActive())
                .as("the rule is about owning this classroom, not about being a teacher")
                .isTrue();
    }

    @Test
    void joiningTwiceIsRejected() {
        Account teacher = teacher("Prof. Exemplo");
        ClassroomView classroom = classroom(teacher);
        Invite invite = invite(teacher, classroom);
        Account student = student();
        invites.redeem(student.id(), invite.code());

        assertThatThrownBy(() -> invites.redeem(student.id(), invite.code()))
                .as("invariant 3")
                .isInstanceOf(AlreadyEnrolledException.class);
        assertThat(activeEnrollmentCount(classroom.id())).isEqualTo(1);
    }

    @Test
    void aStudentWhoLeftMayJoinTheSameClassroomAgain() {
        Account teacher = teacher("Prof. Exemplo");
        ClassroomView classroom = classroom(teacher);
        Invite invite = invite(teacher, classroom);
        Account student = student();
        EnrollmentView first = invites.redeem(student.id(), invite.code());
        classrooms.leave(student.id(), first.id());

        EnrollmentView second = invites.redeem(student.id(), invite.code());

        assertThat(second.id())
                .as("the constraint is on active enrollments, so a history of memberships is "
                        + "kept and rejoining writes a new one")
                .isNotEqualTo(first.id());
        assertThat(jdbc.queryForObject(
                "select count(*) from enrollment where classroom_id = ? and account_id = ?",
                Integer.class, classroom.id(), student.id())).isEqualTo(2);
    }

    @Test
    void aCodeIsAcceptedTheWayAPersonWouldTypeIt() {
        Account teacher = teacher("Prof. Exemplo");
        Invite invite = invite(teacher, classroom(teacher));
        Account student = student();

        // Lower case, a separator somebody added for readability, and the letters Crockford
        // leaves out precisely because a reader types them for the digits they resemble.
        String asTyped = invite.code().toLowerCase(Locale.ROOT)
                .replace('0', 'O')
                .replace('1', 'l');
        String withSeparator = asTyped.substring(0, 5) + "-" + asTyped.substring(5);

        EnrollmentView enrollment = invites.redeem(student.id(), withSeparator);

        assertThat(enrollment.isActive())
                .as("refusing a code that was read correctly and typed the way it looked is a "
                        + "support ticket, not a security measure")
                .isTrue();
    }
}
