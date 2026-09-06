package br.com.sinapse.platform.readmodel.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.sinapse.platform.curriculum.api.TopicView;
import br.com.sinapse.platform.educational.api.ClassroomView;
import br.com.sinapse.platform.identity.internal.domain.Account;
import br.com.sinapse.platform.learningrecord.api.RecallRating;
import br.com.sinapse.platform.planning.api.PlannedSessionView;
import br.com.sinapse.platform.planning.api.StudyPlanView;
import br.com.sinapse.platform.readmodel.support.QueryCounter.Measured;
import br.com.sinapse.platform.readmodel.support.ReadModelIntegrationTest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Every read model issues the same number of statements whatever the size of the data.
 *
 * <p>The prompt for these models asks for this explicitly, and the reason is worth restating:
 * an N+1 introduced later passes every functional test in this repository. The agenda would
 * still show the right topics, the panel the right minutes, the class list the right students —
 * and the only symptom would be latency, in production, on the screens that matter most.
 *
 * <p>Each test measures the same read twice: once against a small fixture and once against a
 * larger one, and asserts that the two counts are equal. The absolute number is asserted too,
 * because a read that grew from six statements to nine without growing with the data is still a
 * regression worth seeing, and a bare "did not grow" would hide it.
 */
class QueryCountIntegrationTest extends ReadModelIntegrationTest {

    /** Statements the initial screen costs: the state, the gate, and one read per module. */
    private static final long STATE_QUERIES = 9;

    /** Statements the agenda costs: the gate, the zone, the schedule, the catalogue, executions. */
    private static final long AGENDA_QUERIES = 7;

    /** Statements a plan summary costs. */
    private static final long PLAN_SUMMARY_QUERIES = 7;

    /** Statements a panel costs: two gates, four reads of the record, and the catalogue. */
    private static final long PANEL_QUERIES = 12;

    /** Statements a class list costs, whatever the size of the classroom. */
    private static final long ROSTER_QUERIES = 9;

    @Test
    void theInitialScreenCostsAFixedNumberOfQueries() {
        Account small = studentReadyToPlan(2);
        planFor(small);
        Account large = studentReadyToPlan(30);
        planFor(large);

        Measured<?> forSmall = queries.measure(() -> states.of(small.id()));
        Measured<?> forLarge = queries.measure(() -> states.of(large.id()));

        assertThat(forSmall.queries()).isEqualTo(STATE_QUERIES);
        assertThat(forLarge.queries()).isEqualTo(forSmall.queries());
    }

    /**
     * Forty scheduled sessions cost what two do.
     *
     * <p>This is the case section 4 of the API contract predicts by name: a day's agenda with
     * forty sessions making forty topic lookups.
     */
    @Test
    void theAgendaCostsTheSameForFortySessionsAsForTwo() {
        Account small = studentReadyToPlan(2);
        planFor(small);
        Account large = studentReadyToPlan(40);
        StudyPlanView plan = planFor(large);
        for (PlannedSessionView session : directory.sessionsOfPlan(plan.id())) {
            executed(large, session.topicId(), session.id(), Duration.ofHours(3));
        }
        assertThat(directory.sessionsOfPlan(plan.id()))
                .as("the stub core fills every availability slot it is given, which is a whole "
                        + "month of weekday evenings")
                .hasSizeGreaterThanOrEqualTo(20);

        Measured<?> forSmall = queries.measure(
                () -> agendas.of(small.id(), windowStart(), windowEnd()));
        Measured<?> forLarge = queries.measure(
                () -> agendas.of(large.id(), windowStart(), windowEnd()));

        assertThat(forSmall.queries()).isEqualTo(AGENDA_QUERIES);
        assertThat(forLarge.queries()).isEqualTo(forSmall.queries());
    }

    @Test
    void aPlanSummaryCostsTheSameHoweverManySessionsThePlanHas() {
        Account small = studentReadyToPlan(2);
        StudyPlanView smallPlan = planFor(small);
        Account large = studentReadyToPlan(40);
        StudyPlanView largePlan = planFor(large);

        Measured<?> forSmall = queries.measure(() -> summaries.of(small.id(), smallPlan.id()));
        Measured<?> forLarge = queries.measure(() -> summaries.of(large.id(), largePlan.id()));

        assertThat(forSmall.queries()).isEqualTo(PLAN_SUMMARY_QUERIES);
        assertThat(forLarge.queries()).isEqualTo(forSmall.queries());
    }

    @Test
    void aPanelCostsTheSameForAWholeTermOfSessionsAsForOne() {
        Account teacher = teacherAccount();
        ClassroomView classroom = classroomOf(teacher);
        Account quiet = student();
        Account busy = student();
        enrol(quiet, teacher, classroom);
        enrol(busy, teacher, classroom);

        studied(quiet, topic().id(), Duration.ofDays(1), 30, RecallRating.GOOD);
        for (TopicView topic : manyTopics(12)) {
            for (int session = 1; session <= 4; session++) {
                studied(busy, topic.id(), Duration.ofDays(session), 30, RecallRating.GOOD);
            }
        }

        Measured<?> forQuiet = queries.measure(() -> panels.of(teacher.id(), classroom.id(),
                quiet.id(), windowStart(), windowEnd()));
        Measured<?> forBusy = queries.measure(() -> panels.of(teacher.id(), classroom.id(),
                busy.id(), windowStart(), windowEnd()));

        assertThat(forQuiet.queries()).isEqualTo(PANEL_QUERIES);
        assertThat(forBusy.queries()).isEqualTo(forQuiet.queries());
    }

    /**
     * The one that matters most: a classroom of forty costs what a classroom of one does.
     *
     * <p>Every batch lookup added to a module's {@code api} for these read models exists for
     * this assertion. Without them the class list is four queries per student.
     */
    @Test
    void theClassListCostsTheSameForFortyStudentsAsForOne() {
        Account teacher = teacherAccount();
        ClassroomView tiny = classroomOf(teacher);
        ClassroomView full = classroomOf(teacher);
        TopicView topic = topic();

        // Every student gets a plan with a session already past due and one still to come, so
        // that both reads take the same branches: a class list where nothing had fallen due
        // would skip the execution lookup and measure a shorter path than the real one.
        enrolWithHistory(teacher, tiny, topic);
        for (int index = 0; index < 40; index++) {
            enrolWithHistory(teacher, full, topic);
        }

        Measured<?> forTiny = queries.measure(
                () -> rosters.of(teacher.id(), tiny.id(), windowStart(), windowEnd()));
        Measured<?> forFull = queries.measure(
                () -> rosters.of(teacher.id(), full.id(), windowStart(), windowEnd()));

        assertThat(forFull.queries())
                .as("forty students, and not one query more than one student costs")
                .isEqualTo(forTiny.queries());
        assertThat(forTiny.queries()).isEqualTo(ROSTER_QUERIES);
    }

    /** A student of the classroom with a due session, an execution of it, and recorded time. */
    private void enrolWithHistory(Account teacher, ClassroomView classroom, TopicView topic) {
        Account student = student();
        enrol(student, teacher, classroom);
        StudyPlanView plan = planWithDueSessions(student, topic.id(), 2, 1);
        PlannedSessionView due = directory.sessionsOfPlan(plan.id()).getFirst();
        executed(student, topic.id(), due.id(), Duration.ofDays(2));
    }

    private List<TopicView> manyTopics(int count) {
        List<TopicView> topics = new ArrayList<>();
        var subject = subject();
        for (int index = 0; index < count; index++) {
            topics.add(topicOf(subject));
        }
        return topics;
    }
}
