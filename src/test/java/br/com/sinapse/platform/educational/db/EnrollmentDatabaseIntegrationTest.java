package br.com.sinapse.platform.educational.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.sinapse.platform.educational.api.ClassroomView;
import br.com.sinapse.platform.educational.api.EnrollmentView;
import br.com.sinapse.platform.educational.support.EducationalIntegrationTest;
import br.com.sinapse.platform.identity.internal.domain.Account;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * The guarantees that belong to the database, checked where they live.
 *
 * <p>Everything here goes through plain SQL. An assertion made through the service would only
 * prove that the service currently checks something; these constraints exist because a service
 * can stop checking, and because two concurrent requests can both pass a check made in code.
 */
class EnrollmentDatabaseIntegrationTest extends EducationalIntegrationTest {

    private static final String INSERT_ENROLLMENT = """
            insert into enrollment (id, classroom_id, account_id, enrolled_at)
            values (?, ?, ?, now())
            """;

    @Test
    void thePartialIndexRefusesASecondActiveEnrollmentForTheSamePair() {
        Account teacher = teacher("Prof. Exemplo");
        ClassroomView classroom = classroom(teacher);
        Account student = student();
        jdbc.update(INSERT_ENROLLMENT, UUID.randomUUID(), classroom.id(), student.id());

        assertThatThrownBy(() -> jdbc.update(INSERT_ENROLLMENT, UUID.randomUUID(), classroom.id(),
                student.id()))
                .as("invariant 3 is a guarantee of the database, not a discipline of the code: "
                        + "two concurrent redemptions both pass a check made in the service")
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ux_enrollment_active");

        assertThat(activeEnrollmentCount(classroom.id())).isEqualTo(1);
    }

    @Test
    void theIndexAllowsANewEnrollmentOnceTheFirstHasEnded() {
        Account teacher = teacher("Prof. Exemplo");
        ClassroomView classroom = classroom(teacher);
        Account student = student();
        UUID first = UUID.randomUUID();
        jdbc.update(INSERT_ENROLLMENT, first, classroom.id(), student.id());
        jdbc.update("update enrollment set ended_at = now(), ended_reason = 'STUDENT_LEFT' "
                + "where id = ?", first);

        jdbc.update(INSERT_ENROLLMENT, UUID.randomUUID(), classroom.id(), student.id());

        assertThat(jdbc.queryForObject(
                "select count(*) from enrollment where classroom_id = ? and account_id = ?",
                Integer.class, classroom.id(), student.id()))
                .as("the index covers active enrollments, so the history of memberships accrues")
                .isEqualTo(2);
    }

    @Test
    void theTriggerRefusesADelete() {
        Account teacher = teacher("Prof. Exemplo");
        Account student = student();
        EnrollmentView enrollment = invites.redeem(student.id(),
                invite(teacher, classroom(teacher)).code());

        assertThatThrownBy(() -> jdbc.update("delete from enrollment where id = ?", enrollment.id()))
                .as("this record is what justifies the access the teacher had while it was "
                        + "active; deleting it would leave that access unexplained")
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("cannot be deleted");

        assertThat(jdbc.queryForObject("select count(*) from enrollment where id = ?",
                Integer.class, enrollment.id())).isEqualTo(1);
    }

    @Test
    void theTriggerRefusesADeleteOfAnEnrollmentThatAlreadyEnded() {
        Account teacher = teacher("Prof. Exemplo");
        Account student = student();
        EnrollmentView enrollment = invites.redeem(student.id(),
                invite(teacher, classroom(teacher)).code());
        classrooms.leave(student.id(), enrollment.id());

        assertThatThrownBy(() -> jdbc.update("delete from enrollment where id = ?", enrollment.id()))
                .as("ended is not the same as removable: the record of a past membership is "
                        + "exactly the one somebody would be tempted to clean up")
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("cannot be deleted");
    }

    @Test
    void anEndedEnrollmentMustSayWhy() {
        Account teacher = teacher("Prof. Exemplo");
        Account student = student();
        EnrollmentView enrollment = invites.redeem(student.id(),
                invite(teacher, classroom(teacher)).code());

        assertThatThrownBy(() -> jdbc.update(
                "update enrollment set ended_at = now() where id = ?", enrollment.id()))
                .as("a timestamp with no reason records that access stopped and not what "
                        + "stopped it, which is half a record")
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_enrollment_reason");
    }

    @Test
    void anInviteCodeIsUniqueAcrossTheWholeTableIncludingRevokedOnes() {
        Account teacher = teacher("Prof. Exemplo");
        ClassroomView classroom = classroom(teacher);
        String code = invite(teacher, classroom).code();
        jdbc.update("update invite set revoked_at = now() where code = ?", code);

        assertThatThrownBy(() -> jdbc.update("""
                insert into invite (id, classroom_id, code, created_by, expires_at)
                select ?, ?, ?, created_by, now() + interval '7 days' from invite where code = ?
                """, UUID.randomUUID(), classroom.id(), code, code))
                .as("a code must never be ambiguous, even historically: somebody holding an old "
                        + "one has to be told it is dead, not admitted somewhere new")
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ux_invite_code");
    }

    @Test
    void anInviteCannotBeUsedMoreTimesThanItAllows() {
        Account teacher = teacher("Prof. Exemplo");
        ClassroomView classroom = classroom(teacher);
        UUID inviteId = invite(teacher, classroom, Duration.ofDays(7), 1).id();

        assertThatThrownBy(() -> jdbc.update("update invite set use_count = 2 where id = ?", inviteId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_invite_use_count");
    }
}
