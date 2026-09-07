package br.com.sinapse.platform.readmodel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.sinapse.platform.identity.api.ConsentPurpose;
import br.com.sinapse.platform.identity.internal.domain.Account;
import br.com.sinapse.platform.identity.support.IdentityFixtures;
import br.com.sinapse.platform.learningrecord.api.SessionStatus;
import br.com.sinapse.platform.planning.api.PlannedSessionView;
import br.com.sinapse.platform.planning.api.StudyPlanView;
import br.com.sinapse.platform.readmodel.api.DailyAgendaView;
import br.com.sinapse.platform.readmodel.internal.error.InvalidReadWindowException;
import br.com.sinapse.platform.readmodel.internal.error.LearningDataNotReadableException;
import br.com.sinapse.platform.readmodel.support.ReadModelIntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** {@code AgendaDoDia}: one screen, four modules. */
class AgendaReadModelIntegrationTest extends ReadModelIntegrationTest {

    @Test
    void everyScheduledSlotCarriesItsTopicAndSubjectNames() {
        Account student = studentReadyToPlan(3);
        StudyPlanView plan = planFor(student);
        List<PlannedSessionView> scheduled = directory.sessionsOfPlan(plan.id());

        DailyAgendaView agenda = agendas.of(student.id(), windowStart(), windowEnd());

        List<DailyAgendaView.Entry> entries = agenda.days().stream()
                .flatMap(day -> day.entries().stream())
                .toList();
        assertThat(entries).hasSize(scheduled.size());
        assertThat(entries).allSatisfy(entry -> {
            assertThat(entry.topicName()).isNotBlank();
            assertThat(entry.subjectId()).isNotNull();
            assertThat(entry.subjectName()).isNotBlank();
            assertThat(entry.plannedDurationMinutes()).isPositive();
        });
    }

    /**
     * The matching this read model exists for.
     *
     * <p>Without it the client would fetch planned sessions and executed sessions separately and
     * pair them in the browser, which is business logic in the wrong place.
     */
    @Test
    void theExecutionOfASlotIsAttachedToTheSlotItExecuted() {
        Account student = studentReadyToPlan(3);
        StudyPlanView plan = planFor(student);
        PlannedSessionView slot = directory.sessionsOfPlan(plan.id()).getFirst();
        executed(student, slot.topicId(), slot.id(), Duration.ofHours(2));

        List<DailyAgendaView.Entry> entries = entriesOf(
                agendas.of(student.id(), windowStart(), windowEnd()));

        DailyAgendaView.Entry executedEntry = entries.stream()
                .filter(entry -> entry.plannedSessionId().equals(slot.id()))
                .findFirst()
                .orElseThrow();
        assertThat(executedEntry.execution()).isNotNull();
        assertThat(executedEntry.execution().status()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(executedEntry.execution().actualDurationMinutes()).isEqualTo(45);

        assertThat(entries).filteredOn(entry -> !entry.plannedSessionId().equals(slot.id()))
                .allSatisfy(entry -> assertThat(entry.execution()).isNull());
    }

    /**
     * Days are the holder's days, not the server's.
     *
     * <p>The fixture zone is three hours behind UTC, so a session scheduled in the small hours of
     * a UTC morning belongs to the previous evening for the student. Grouping in the server's
     * zone would put it on the wrong day for everyone west of Greenwich, which is everyone this
     * platform is for.
     */
    @Test
    void daysAreGroupedInTheHoldersZone() {
        Account student = studentReadyToPlan(2);
        StudyPlanView plan = planFor(student);
        List<PlannedSessionView> scheduled = directory.sessionsOfPlan(plan.id());

        DailyAgendaView agenda = agendas.of(student.id(), windowStart(), windowEnd());

        assertThat(agenda.days()).allSatisfy(day -> {
            List<LocalDate> expected = scheduled.stream()
                    .map(session -> LocalDate.ofInstant(session.scheduledStart(),
                            IdentityFixtures.DEFAULT_ZONE))
                    .distinct()
                    .toList();
            assertThat(expected).contains(day.date());
        });
        assertThat(agenda.days()).isSortedAccordingTo(
                java.util.Comparator.comparing(DailyAgendaView.Day::date));
    }

    @Test
    void aStudentWithNothingPlannedGetsAnEmptyAgendaRatherThanAFailure() {
        Account student = student();

        assertThat(agendas.of(student.id(), windowStart(), windowEnd()).days()).isEmpty();
    }

    @Test
    void aWindowWiderThanTheServerAnswersIsRefusedRatherThanTrimmed() {
        Account student = student();
        Instant from = clock.instant().minus(Duration.ofDays(60));

        assertThatThrownBy(() -> agendas.of(student.id(), from, clock.instant()))
                .isInstanceOf(InvalidReadWindowException.class);
    }

    @Test
    void anEmptyWindowIsRefusedRatherThanAnsweredWithNothing() {
        Account student = student();
        Instant now = clock.instant();

        assertThatThrownBy(() -> agendas.of(student.id(), now, now))
                .isInstanceOf(InvalidReadWindowException.class);
    }

    @Test
    void aHolderWhoseDataMayNotBeProcessedReadsNothing() {
        Account student = studentReadyToPlan(3);
        planFor(student);
        consents.revoke(student.id(), ConsentPurpose.LEARNING_DATA_PROCESSING);

        assertThatThrownBy(() -> agendas.of(student.id(), windowStart(), windowEnd()))
                .isInstanceOf(LearningDataNotReadableException.class);
    }

    private static List<DailyAgendaView.Entry> entriesOf(DailyAgendaView agenda) {
        return agenda.days().stream().flatMap(day -> day.entries().stream()).toList();
    }
}
