package br.com.sinapse.platform.educational.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.sinapse.platform.educational.api.ClassroomStatus;
import br.com.sinapse.platform.educational.api.ClassroomView;
import br.com.sinapse.platform.educational.api.EnrollmentEndReason;
import br.com.sinapse.platform.educational.internal.domain.Invite;
import br.com.sinapse.platform.educational.internal.error.ClassroomAlreadyArchivedException;
import br.com.sinapse.platform.educational.internal.error.InviteNotRedeemableException;
import br.com.sinapse.platform.educational.internal.error.NotTheClassroomOwnerException;
import br.com.sinapse.platform.educational.internal.error.UnknownEnrollmentException;
import br.com.sinapse.platform.educational.internal.service.ClassroomService;
import br.com.sinapse.platform.educational.support.EducationalIntegrationTest;
import br.com.sinapse.platform.identity.internal.domain.Account;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Invariant 6, and the ownership checks around it. */
class ClassroomArchivalIntegrationTest extends EducationalIntegrationTest {

    @Test
    void archivingEndsEveryActiveEnrollmentAndRevokesEveryOutstandingInvite() {
        Account teacher = teacher("Prof. Exemplo");
        ClassroomView classroom = classroom(teacher);
        Invite redeemed = invite(teacher, classroom);
        Invite untouched = invite(teacher, classroom);
        invites.redeem(student().id(), redeemed.code());
        invites.redeem(student().id(), redeemed.code());

        ClassroomService.ArchiveResult result = classrooms.archive(teacher.id(), classroom.id());

        assertThat(result).isEqualTo(new ClassroomService.ArchiveResult(2, 2));
        assertThat(activeEnrollmentCount(classroom.id())).isZero();
        assertThat(jdbc.queryForList(
                "select distinct ended_reason from enrollment where classroom_id = ?",
                String.class, classroom.id()))
                .containsExactly(EnrollmentEndReason.CLASSROOM_ARCHIVED.name());
        assertThat(jdbc.queryForObject(
                "select count(*) from invite where classroom_id = ? and revoked_at is null",
                Integer.class, classroom.id())).isZero();
        assertThat(jdbc.queryForObject("select status from classroom where id = ?",
                String.class, classroom.id())).isEqualTo(ClassroomStatus.ARCHIVED.name());
        assertThat(untouched.id()).isNotEqualTo(redeemed.id());
    }

    @Test
    void anArchivingThatFailsChangesNothing() {
        Account teacher = teacher("Prof. Exemplo");
        ClassroomView classroom = classroom(teacher);
        Invite invite = invite(teacher, classroom);
        Account student = student();
        invites.redeem(student.id(), invite.code());

        // A state nothing in this module can produce: the classroom marked archived while an
        // enrollment is still active and an invite is still live. Archiving it has to refuse
        // as a whole rather than tidy up half of it, because a half-applied archiving would
        // leave enrollments ended under a classroom that no operation had actually closed.
        jdbc.update("update classroom set status = 'ARCHIVED', archived_at = now() where id = ?",
                classroom.id());

        assertThatThrownBy(() -> classrooms.archive(teacher.id(), classroom.id()))
                .isInstanceOf(ClassroomAlreadyArchivedException.class);

        assertThat(activeEnrollmentCount(classroom.id()))
                .as("the enrollment was not ended on the way out")
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select revoked_at is null from invite where id = ?",
                Boolean.class, invite.id()))
                .as("and the invite was not revoked either")
                .isTrue();
    }

    @Test
    void archivingTwiceIsRefused() {
        Account teacher = teacher("Prof. Exemplo");
        ClassroomView classroom = classroom(teacher);
        classrooms.archive(teacher.id(), classroom.id());

        assertThatThrownBy(() -> classrooms.archive(teacher.id(), classroom.id()))
                .as("a second archiving would move the timestamp and lose when the teacher's "
                        + "access actually ended")
                .isInstanceOf(ClassroomAlreadyArchivedException.class);
    }

    @Test
    void anArchivedClassroomIssuesNoMoreInvites() {
        Account teacher = teacher("Prof. Exemplo");
        ClassroomView classroom = classroom(teacher);
        classrooms.archive(teacher.id(), classroom.id());

        assertThatThrownBy(() -> invite(teacher, classroom))
                .isInstanceOf(InviteNotRedeemableException.class);
    }

    @Test
    void anotherTeacherCannotArchiveTheClassroom() {
        Account owner = teacher("Prof. Dono");
        Account other = teacher("Prof. Alheio");
        ClassroomView classroom = classroom(owner);

        assertThatThrownBy(() -> classrooms.archive(other.id(), classroom.id()))
                .as("answering not found rather than forbidden, so the route cannot be walked "
                        + "to discover which classrooms exist")
                .isInstanceOf(NotTheClassroomOwnerException.class);
        assertThat(jdbc.queryForObject("select status from classroom where id = ?",
                String.class, classroom.id())).isEqualTo(ClassroomStatus.OPEN.name());
    }

    @Test
    void aStudentCannotEndSomebodyElsesEnrollment() {
        Account teacher = teacher("Prof. Exemplo");
        Account student = student();
        Account other = student();
        UUID enrollmentId = invites.redeem(student.id(),
                invite(teacher, classroom(teacher)).code()).id();

        assertThatThrownBy(() -> classrooms.leave(other.id(), enrollmentId))
                .isInstanceOf(UnknownEnrollmentException.class);
        assertThat(jdbc.queryForObject("select ended_at is null from enrollment where id = ?",
                Boolean.class, enrollmentId)).isTrue();
    }

    @Test
    void removingAStudentWhoIsNotThereIsRefused() {
        Account teacher = teacher("Prof. Exemplo");
        ClassroomView classroom = classroom(teacher);

        assertThatThrownBy(() -> classrooms.remove(teacher.id(), classroom.id(), UUID.randomUUID()))
                .isInstanceOf(UnknownEnrollmentException.class);
    }
}
