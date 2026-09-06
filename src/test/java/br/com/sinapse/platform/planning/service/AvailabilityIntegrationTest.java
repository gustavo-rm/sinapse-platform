package br.com.sinapse.platform.planning.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.sinapse.platform.identity.api.ConsentPurpose;
import br.com.sinapse.platform.identity.internal.domain.Account;
import br.com.sinapse.platform.identity.internal.service.ConsentService;
import br.com.sinapse.platform.planning.api.AvailabilityWindowView;
import br.com.sinapse.platform.planning.internal.error.AvailabilityAlreadyClosedException;
import br.com.sinapse.platform.planning.internal.error.OverlappingAvailabilityException;
import br.com.sinapse.platform.planning.internal.error.PlanningDataNotProcessableException;
import br.com.sinapse.platform.planning.internal.error.UnknownAvailabilityWindowException;
import br.com.sinapse.platform.planning.support.PlanningIntegrationTest;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Declaring and closing weekly availability, through the service that owns it. */
class AvailabilityIntegrationTest extends PlanningIntegrationTest {

    /** Generous: the second transaction only has to wait for the first to release the lock. */
    private static final int TIMEOUT_SECONDS = 30;

    private static final LocalDate JANUARY = LocalDate.parse("2026-01-01");
    private static final LocalDate JUNE = LocalDate.parse("2026-06-30");

    @Autowired
    private ConsentService consents;

    @Test
    void aStudentDeclaresTheirWeek() {
        Account student = student();

        AvailabilityWindowView tuesday = declare(student, DayOfWeek.TUESDAY, "19:00", "21:00");
        declare(student, DayOfWeek.THURSDAY, "19:00", "21:00");

        assertThat(tuesday.isClosed()).isFalse();
        assertThat(directory.availabilityOf(student.id()))
                .extracting(AvailabilityWindowView::dayOfWeek)
                .containsExactly(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY);
    }

    @Test
    void anOverlappingWindowOnTheSameDayIsRefused() {
        Account student = student();
        declare(student, DayOfWeek.TUESDAY, "19:00", "21:00");

        assertThatThrownBy(() -> declare(student, DayOfWeek.TUESDAY, "20:00", "22:00"))
                .as("an hour declared twice would be counted twice by whatever allocates study "
                        + "time, and the plan would be built on hours that do not exist")
                .isInstanceOf(OverlappingAvailabilityException.class);

        assertThat(directory.availabilityOf(student.id())).hasSize(1);
    }

    @Test
    void aWindowThatOnlyTouchesTheEndpointIsAccepted() {
        Account student = student();
        declare(student, DayOfWeek.MONDAY, "09:00", "11:00");

        assertThatCode(() -> declare(student, DayOfWeek.MONDAY, "11:00", "13:00"))
                .as("two study blocks in a row is a week people actually have")
                .doesNotThrowAnyException();

        assertThat(directory.availabilityOf(student.id())).hasSize(2);
    }

    @Test
    void theSameHourOnAnotherDayOrAnotherStudentIsAccepted() {
        Account student = student();
        Account other = student();
        declare(student, DayOfWeek.TUESDAY, "19:00", "21:00");

        assertThatCode(() -> declare(student, DayOfWeek.WEDNESDAY, "19:00", "21:00"))
                .doesNotThrowAnyException();
        assertThatCode(() -> declare(other, DayOfWeek.TUESDAY, "19:00", "21:00"))
                .as("the rule is about one student's week, not about the hour in the abstract")
                .doesNotThrowAnyException();
    }

    /**
     * The one flow section 9.1 exists for: a routine changes without losing what it was.
     */
    @Test
    void closingAWindowFreesTheHourWithoutErasingWhatItSaid() {
        Account student = student();
        AvailabilityWindowView original = availability.declare(student.id(), DayOfWeek.TUESDAY,
                LocalTime.parse("19:00"), LocalTime.parse("21:00"), JANUARY, null);

        assertThatThrownBy(() -> availability.declare(student.id(), DayOfWeek.TUESDAY,
                LocalTime.parse("19:00"), LocalTime.parse("21:00"), JUNE, null))
                .isInstanceOf(OverlappingAvailabilityException.class);

        availability.close(student.id(), original.id(), JUNE.minusDays(1));
        availability.declare(student.id(), DayOfWeek.TUESDAY, LocalTime.parse("19:00"),
                LocalTime.parse("21:00"), JUNE, null);

        assertThat(directory.availabilityOf(student.id()))
                .as("the plan generated in March was built against the first window, and that "
                        + "window still says what it said")
                .hasSize(2);
        assertThat(directory.availabilityOn(student.id(), JANUARY.plusMonths(2)))
                .singleElement()
                .satisfies(window -> assertThat(window.id()).isEqualTo(original.id()));
        assertThat(directory.availabilityOn(student.id(), JUNE))
                .singleElement()
                .satisfies(window -> assertThat(window.id()).isNotEqualTo(original.id()));
    }

    @Test
    void aWindowIsClosedOnceAndOnlyByItsOwner() {
        Account student = student();
        Account other = student();
        AvailabilityWindowView window = declare(student, DayOfWeek.TUESDAY, "19:00", "21:00");

        assertThatThrownBy(() -> availability.close(other.id(), window.id(), JUNE))
                .as("a window that exists and one belonging to somebody else answer the same "
                        + "way; separating them would disclose another student's week")
                .isInstanceOf(UnknownAvailabilityWindowException.class);
        assertThatThrownBy(() -> availability.close(student.id(), UUID.randomUUID(), JUNE))
                .isInstanceOf(UnknownAvailabilityWindowException.class);

        availability.close(student.id(), window.id(), JUNE);

        assertThatThrownBy(() -> availability.close(student.id(), window.id(), JUNE.plusMonths(1)))
                .isInstanceOf(AvailabilityAlreadyClosedException.class);
    }

    @Test
    void anAccountWhoseDataMayNotBeProcessedDeclaresNothing() {
        Account student = student();
        AvailabilityWindowView window = declare(student, DayOfWeek.TUESDAY, "19:00", "21:00");

        consents.revoke(student.id(), ConsentPurpose.LEARNING_DATA_PROCESSING);

        assertThatThrownBy(() -> declare(student, DayOfWeek.THURSDAY, "19:00", "21:00"))
                .isInstanceOf(PlanningDataNotProcessableException.class);
        assertThatThrownBy(() -> availability.close(student.id(), window.id(), JUNE))
                .isInstanceOf(PlanningDataNotProcessableException.class);
    }

    /**
     * Two declarations of the same hour, at the same time.
     *
     * <p>The test the advisory lock exists for. Under read committed neither transaction sees
     * the other's uncommitted row, so the overlap check passes in both — which is exactly the
     * failure mode the prompt's "enforce it in the application" leaves open, and why the check
     * is serialised per account rather than merely written down.
     *
     * <p>Which of the two loses is not determined, and the assertion does not care. What it
     * asserts is that exactly one does.
     */
    @Test
    void twoSimultaneousOverlappingDeclarationsLeaveExactlyOneWindow() throws Exception {
        Account student = student();
        CyclicBarrier bothReady = new CyclicBarrier(2);
        ExecutorService threads = Executors.newFixedThreadPool(2);

        try {
            Future<Optional<Class<?>>> one =
                    threads.submit(declaring(bothReady, student, "19:00", "21:00"));
            Future<Optional<Class<?>>> other =
                    threads.submit(declaring(bothReady, student, "20:00", "22:00"));

            List<Optional<Class<?>>> outcomes = List.of(one.get(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    other.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));

            assertThat(outcomes.stream().filter(Optional::isEmpty).count())
                    .as("exactly one of the two declarations commits")
                    .isEqualTo(1);
            assertThat(outcomes.stream().filter(Optional::isPresent).map(Optional::get))
                    .as("and the other is refused for the reason we expect, not by chance")
                    .containsExactly(OverlappingAvailabilityException.class);
        } finally {
            threads.shutdownNow();
        }

        assertThat(directory.availabilityOf(student.id()))
                .as("a check made in application code is not a guarantee unless something "
                        + "serialises it")
                .hasSize(1);
    }

    private Callable<Optional<Class<?>>> declaring(CyclicBarrier bothReady, Account student,
            String start, String end) {

        return () -> {
            bothReady.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            try {
                declare(student, DayOfWeek.TUESDAY, start, end);
                return Optional.empty();
            } catch (RuntimeException refusal) {
                return Optional.of(refusal.getClass());
            }
        };
    }

    private AvailabilityWindowView declare(Account student, DayOfWeek day, String start,
            String end) {

        return availability.declare(student.id(), day, LocalTime.parse(start),
                LocalTime.parse(end), JANUARY, null);
    }
}
