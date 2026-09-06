package br.com.sinapse.platform.educational.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.sinapse.platform.educational.api.ClassroomView;
import br.com.sinapse.platform.educational.api.EducationalDirectory;
import br.com.sinapse.platform.educational.api.EnrollmentView;
import br.com.sinapse.platform.educational.internal.error.InviteLifetimeOutOfRangeException;
import br.com.sinapse.platform.educational.internal.error.TeacherRecordMissingException;
import br.com.sinapse.platform.educational.support.EducationalIntegrationTest;
import br.com.sinapse.platform.identity.internal.domain.Account;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Reads, the teacher record behind them, and the edges around issuing an invite. */
class EducationalDirectoryIntegrationTest extends EducationalIntegrationTest {

    @Autowired
    private EducationalDirectory directory;

    @Test
    void theDirectoryAnswersAboutManyStudentsInOneCall() {
        Account teacher = teacher("Prof. Exemplo");
        ClassroomView classroom = classroom(teacher);
        Account first = student();
        Account second = student();
        String code = invite(teacher, classroom).code();
        invites.redeem(first.id(), code);
        invites.redeem(second.id(), code);

        List<EnrollmentView> found = directory.activeEnrollmentsOf(
                List.of(first.id(), second.id(), UUID.randomUUID()));

        assertThat(found)
                .as("rule R7: a read model asks about a whole classroom at once, and a lookup "
                        + "per student would give it one query per row")
                .hasSize(2)
                .allMatch(EnrollmentView::isActive);
        assertThat(directory.activeEnrollmentsOf(List.of())).isEmpty();
    }

    @Test
    void anAccountThatIsNotATeacherOwnsNoClassrooms() {
        Account student = student();

        assertThat(directory.classroomsOwnedBy(student.id()))
                .as("answering empty rather than failing: not being a teacher is not an error, "
                        + "it is the normal case")
                .isEmpty();
        assertThat(directory.classroom(UUID.randomUUID())).isEmpty();
    }

    @Test
    void theDirectoryListsAClassroomAfterItIsArchived() {
        Account teacher = teacher("Prof. Exemplo");
        ClassroomView classroom = classroom(teacher);
        classrooms.archive(teacher.id(), classroom.id());

        assertThat(directory.classroomsOwnedBy(teacher.id()))
                .as("a teacher still has to be able to find the classroom they closed")
                .hasSize(1);
    }

    @Test
    void anAccountWithTheRoleButNoRecordCannotOpenAClassroom() {
        Account account = identity.activeAdult(identity.uniqueEmail());
        grantTeacherRole(account.id());

        assertThatThrownBy(() -> classrooms.open(account.id(), "Turma", Set.of()))
                .as("the role authorises the login; the record carries the name a student sees "
                        + "on a preview, and nothing derives one because identity stores none")
                .isInstanceOf(TeacherRecordMissingException.class);
    }

    @Test
    void recordingATeacherTwiceRevisesTheSameRecord() {
        Account account = identity.activeAdult(identity.uniqueEmail());
        grantTeacherRole(account.id());
        UUID first = teachers.record(account.id(), "Prof. Antigo", "Instituição A").id();

        UUID second = teachers.record(account.id(), "Prof. Nova", "Instituição B").id();

        assertThat(second).isEqualTo(first);
        assertThat(jdbc.queryForObject("select display_name from teacher where id = ?",
                String.class, first)).isEqualTo("Prof. Nova");
        assertThat(jdbc.queryForObject("select count(*) from teacher where account_id = ?",
                Integer.class, account.id())).isEqualTo(1);
    }

    @Test
    void anInviteCannotBeAskedToLastForeverOrForNoTime() {
        Account teacher = teacher("Prof. Exemplo");
        ClassroomView classroom = classroom(teacher);

        assertThatThrownBy(() -> invite(teacher, classroom, Duration.ofDays(365), null))
                .as("a ceiling is what keeps the mandatory expiry from being a formality a "
                        + "client can set to a century")
                .isInstanceOf(InviteLifetimeOutOfRangeException.class);
        assertThatThrownBy(() -> invite(teacher, classroom, Duration.ZERO, null))
                .isInstanceOf(InviteLifetimeOutOfRangeException.class);
    }
}
