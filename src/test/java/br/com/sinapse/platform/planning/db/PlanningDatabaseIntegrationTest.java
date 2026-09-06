package br.com.sinapse.platform.planning.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.sinapse.platform.identity.internal.domain.Account;
import br.com.sinapse.platform.planning.support.PlanningIntegrationTest;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

/**
 * The guarantees that belong to the database, checked where they live.
 *
 * <p>Everything here goes through plain SQL. An assertion made through a service would only
 * prove that the service currently checks something; these constraints exist because a service
 * can stop checking, because a mapping can be changed by accident, and because two concurrent
 * requests can both pass a check made in code.
 */
class PlanningDatabaseIntegrationTest extends PlanningIntegrationTest {

    @Test
    void thePartialIndexRefusesASecondActivePlan() {
        Account student = student();
        insertPlan(student, "ACTIVE");

        assertThatThrownBy(() -> insertPlan(student, "ACTIVE"))
                .as("two plans in force at once would leave the agenda with no answer to what "
                        + "there is to do today")
                .isInstanceOf(DuplicateKeyException.class)
                .hasMessageContaining("ux_plan_active");

        assertThat(planCount(student.id())).isEqualTo(1);
    }

    @Test
    void theIndexAllowsANewPlanOnceTheFirstIsSuperseded() {
        Account student = student();
        UUID first = insertPlan(student, "ACTIVE");
        jdbc.update("update study_plan set status = 'SUPERSEDED', superseded_at = now() "
                + "where id = ?", first);

        UUID second = insertPlan(student, "ACTIVE");
        jdbc.update("update study_plan set superseded_by_plan_id = ? where id = ?", second, first);

        assertThat(planCount(student.id()))
                .as("the index covers plans in force, so the chain of superseded plans accrues "
                        + "behind it")
                .isEqualTo(2);
        assertThat(planColumn(first, "superseded_by_plan_id", UUID.class)).isEqualTo(second);
    }

    @Test
    void theIndexIsPerAccount() {
        insertPlan(student(), "ACTIVE");

        assertThatCode(() -> insertPlan(student(), "ACTIVE"))
                .as("one student's plan does not stop another from having one")
                .doesNotThrowAnyException();
    }

    @Test
    void theTriggerRefusesEveryUpdateAndDeleteOnAPlannedSession() {
        Account student = student();
        UUID plan = insertPlan(student, "ACTIVE");
        UUID session = insertPlannedSession(plan, topic().id(), 0);

        assertThatThrownBy(() -> jdbc.update(
                "update planned_session set duration_minutes = 90 where id = ?", session))
                .as("executed study sessions carry the identifier of a planned session; editing "
                        + "one would silently rewrite what an executed session says it executed")
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("planned_session is immutable");

        assertThatThrownBy(() -> jdbc.update("delete from planned_session where id = ?", session))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("planned_session is immutable");

        assertThat(plannedSessionCount(plan)).isEqualTo(1);
    }

    @Test
    void theTriggerRefusesEveryChangeToAPlanBeyondItsSupersession() {
        Account student = student();
        Account other = student();
        UUID plan = insertPlan(student, "ACTIVE");
        UUID otherRequest = insertRequest(other);

        assertRefused("horizon_end = horizon_end + 7", plan);
        assertRefused("horizon_start = horizon_start - 7", plan);
        assertRefused("fitness = cast('{\"coverage\": 1.0}' as jsonb)", plan);
        assertRefused("account_id = '" + other.id() + "'", plan);
        assertRefused("generation_request_id = '" + otherRequest + "'", plan);

        assertThat(planColumn(plan, "account_id", UUID.class))
                .as("editing a plan destroys the correspondence with the snapshot that produced "
                        + "it, and that correspondence is the whole of its reproducibility")
                .isEqualTo(student.id());
    }

    @Test
    void theTriggerAllowsTheSupersessionFields() {
        Account student = student();
        UUID plan = insertPlan(student, "ACTIVE");

        assertThatCode(() -> jdbc.update("update study_plan set status = 'SUPERSEDED', "
                + "superseded_at = now() where id = ?", plan))
                .doesNotThrowAnyException();

        assertThat(planColumn(plan, "status", String.class)).isEqualTo("SUPERSEDED");
    }

    @Test
    void aPlanCannotBeSupersededWithoutSayingWhen() {
        Account student = student();
        UUID plan = insertPlan(student, "ACTIVE");

        assertThatThrownBy(() -> jdbc.update(
                "update study_plan set status = 'SUPERSEDED' where id = ?", plan))
                .as("the chain of superseded plans is experimental data about when a student "
                        + "re-plans; a link with no instant would be half of it")
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_plan_supersession");
    }

    @Test
    void thePartialIndexRefusesASecondActiveGoalForTheSameSubject() {
        Account student = student();
        UUID subject = subject().id();
        insertGoal(student, subject, "ACTIVE");

        assertThatThrownBy(() -> insertGoal(student, subject, "ACTIVE"))
                .as("two active goals for one subject would put the same topics into the "
                        + "snapshot twice with two priorities, and the core would have no way "
                        + "to decide which the student meant")
                .isInstanceOf(DuplicateKeyException.class)
                .hasMessageContaining("ux_goal_active_subject");
    }

    @Test
    void theIndexAllowsANewGoalOnceTheFirstIsClosed() {
        Account student = student();
        UUID subject = subject().id();
        UUID first = insertGoal(student, subject, "ACTIVE");
        jdbc.update("update study_goal set status = 'ABANDONED', achieved_at = now() where id = ?",
                first);

        assertThatCode(() -> insertGoal(student, subject, "ACTIVE"))
                .as("abandoning a goal frees the subject; the abandoned one stays on record")
                .doesNotThrowAnyException();
        assertThat(jdbc.queryForObject("select count(*) from study_goal where account_id = ?",
                Integer.class, student.id())).isEqualTo(2);
    }

    @Test
    void thePartialIndexRefusesASecondJobThatHasNotFinished() {
        Account student = student();
        insertRequest(student, "PENDING");

        assertThatThrownBy(() -> insertRequest(student, "RUNNING"))
                .as("each run costs minutes of CPU; without the index an impatient student "
                        + "queues dozens")
                .isInstanceOf(DuplicateKeyException.class)
                .hasMessageContaining("ux_request_active");

        assertThatCode(() -> insertRequest(student, "READY"))
                .as("the index covers the states that have not finished, so the history of runs "
                        + "accrues behind it")
                .doesNotThrowAnyException();
    }

    @Test
    void theSequenceOfAPlanHasNoRepeats() {
        Account student = student();
        UUID plan = insertPlan(student, "ACTIVE");
        UUID topic = topic().id();
        insertPlannedSession(plan, topic, 0);

        assertThatThrownBy(() -> insertPlannedSession(plan, topic, 0))
                .isInstanceOf(DuplicateKeyException.class)
                .hasMessageContaining("uq_planned_sequence");
    }

    @Test
    void anAvailabilityWindowMustBeOnARealDayAndHaveALength() {
        Account student = student();

        assertThatThrownBy(() -> insertWindow(student, 7, "19:00", "21:00"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_availability_day");
        assertThatThrownBy(() -> insertWindow(student, 2, "21:00", "19:00"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_availability_time");

        assertThatCode(() -> insertWindow(student, 0, "09:00", "11:00"))
                .as("Sunday is zero, which is how PostgreSQL itself counts weekdays")
                .doesNotThrowAnyException();
    }

    @Test
    void aGoalPriorityStaysOnTheScale() {
        Account student = student();
        UUID subject = subject().id();

        assertThatThrownBy(() -> jdbc.update("""
                insert into study_goal (id, account_id, subject_id, priority, status)
                values (?, ?, ?, 6, 'ACTIVE')
                """, UUID.randomUUID(), student.id(), subject))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_goal_priority");
    }

    private void assertRefused(String assignment, UUID planId) {
        assertThatThrownBy(() -> jdbc.update(
                "update study_plan set " + assignment + " where id = ?", planId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("study_plan is immutable except for supersession");
    }

    private UUID insertPlan(Account student, String status) {
        UUID id = UUID.randomUUID();
        LocalDate start = LocalDate.now(clock);
        jdbc.update(INSERT_PLAN, id, student.id(), insertRequest(student), start,
                start.plusWeeks(4), status, "{\"coverage\": 0.8}", null, null);
        return id;
    }

    private UUID insertPlannedSession(UUID planId, UUID topicId, int sequenceIndex) {
        UUID id = UUID.randomUUID();
        jdbc.update(INSERT_PLANNED_SESSION, id, planId, topicId, "STUDY",
                Timestamp.from(clock.instant()), 50, sequenceIndex);
        return id;
    }

    private UUID insertRequest(Account student) {
        return insertRequest(student, "READY");
    }

    private UUID insertRequest(Account student, String status) {
        UUID id = UUID.randomUUID();
        LocalDate start = LocalDate.now(clock);
        jdbc.update(INSERT_REQUEST, id, student.id(), status, start, start.plusWeeks(4));
        return id;
    }

    private UUID insertGoal(Account student, UUID subjectId, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into study_goal (id, account_id, subject_id, priority, status)
                values (?, ?, ?, 3, ?)
                """, id, student.id(), subjectId, status);
        return id;
    }

    private void insertWindow(Account student, int dayOfWeek, String start, String end) {
        jdbc.update("""
                insert into study_availability (id, account_id, day_of_week, start_time, end_time,
                                                effective_from)
                values (?, ?, ?, cast(? as time), cast(? as time), ?)
                """, UUID.randomUUID(), student.id(), dayOfWeek, start, end, LocalDate.now(clock));
    }
}
