package br.com.sinapse.platform.planning.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.sinapse.platform.planning.internal.domain.AvailabilityWindow;
import br.com.sinapse.platform.planning.internal.error.AvailabilityAlreadyClosedException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The overlap rule, where it lives.
 *
 * <p>There is no exclusion constraint on {@code study_availability}, so this is the rule and
 * not a convenience in front of one. Each case below is a shape somebody would otherwise have
 * argued about in review.
 */
class AvailabilityWindowTest {

    private static final UUID ACCOUNT = UUID.randomUUID();
    private static final LocalDate JANUARY = LocalDate.parse("2026-01-01");
    private static final LocalDate JUNE = LocalDate.parse("2026-06-30");

    @Test
    void twoWindowsOnTheSameEveningOverlap() {
        AvailabilityWindow evening = window(DayOfWeek.TUESDAY, "19:00", "21:00", JANUARY, null);
        AvailabilityWindow lateEvening = window(DayOfWeek.TUESDAY, "20:00", "22:00", JANUARY, null);

        assertThat(evening.overlaps(lateEvening))
                .as("an hour the student is available in twice would be counted twice by "
                        + "whatever allocates study time, and the plan would be built on hours "
                        + "that do not exist")
                .isTrue();
        assertThat(lateEvening.overlaps(evening))
                .as("the relation is symmetric, and a check that depended on which side it was "
                        + "asked from would pass whenever the caller asked the wrong way round")
                .isTrue();
    }

    @Test
    void oneWindowContainedInAnotherOverlaps() {
        AvailabilityWindow wide = window(DayOfWeek.SATURDAY, "08:00", "18:00", JANUARY, null);
        AvailabilityWindow narrow = window(DayOfWeek.SATURDAY, "10:00", "11:00", JANUARY, null);

        assertThat(wide.overlaps(narrow)).isTrue();
        assertThat(narrow.overlaps(wide)).isTrue();
    }

    @Test
    void windowsThatTouchAtTheEndpointDoNotOverlap() {
        AvailabilityWindow morning = window(DayOfWeek.MONDAY, "09:00", "11:00", JANUARY, null);
        AvailabilityWindow midday = window(DayOfWeek.MONDAY, "11:00", "13:00", JANUARY, null);

        assertThat(morning.overlaps(midday))
                .as("nine to eleven and eleven to one are two study blocks in a row, which is a "
                        + "week people actually have; the time range is half-open for that reason")
                .isFalse();
    }

    @Test
    void windowsOnDifferentDaysNeverOverlap() {
        AvailabilityWindow tuesday = window(DayOfWeek.TUESDAY, "19:00", "21:00", JANUARY, null);
        AvailabilityWindow wednesday = window(DayOfWeek.WEDNESDAY, "19:00", "21:00", JANUARY, null);

        assertThat(tuesday.overlaps(wednesday)).isFalse();
    }

    @Test
    void windowsWhoseValidityDoesNotMeetDoNotOverlap() {
        AvailabilityWindow firstTerm = window(DayOfWeek.TUESDAY, "19:00", "21:00", JANUARY, JUNE);
        AvailabilityWindow secondTerm = window(DayOfWeek.TUESDAY, "19:00", "21:00",
                JUNE.plusDays(1), null);

        assertThat(firstTerm.overlaps(secondTerm))
                .as("this is exactly what closing a window and opening a new one produces, and "
                        + "if it counted as an overlap a student could never change their routine")
                .isFalse();
    }

    @Test
    void windowsWhoseValidityMeetsOnASingleDayOverlap() {
        AvailabilityWindow firstTerm = window(DayOfWeek.TUESDAY, "19:00", "21:00", JANUARY, JUNE);
        AvailabilityWindow overlappingTerm = window(DayOfWeek.TUESDAY, "19:00", "21:00", JUNE, null);

        assertThat(firstTerm.overlaps(overlappingTerm))
                .as("both ends of the validity range are inclusive: a window closed on the "
                        + "thirtieth still applied on the thirtieth")
                .isTrue();
    }

    @Test
    void anOpenEndedWindowReachesForward() {
        AvailabilityWindow openEnded = window(DayOfWeek.TUESDAY, "19:00", "21:00", JANUARY, null);
        AvailabilityWindow muchLater = window(DayOfWeek.TUESDAY, "19:00", "21:00",
                JANUARY.plusYears(5), null);

        assertThat(openEnded.overlaps(muchLater))
                .as("a window with no end has no end; treating a null as a short range would "
                        + "let a second Tuesday evening in five years from now")
                .isTrue();
    }

    @Test
    void aWindowAppliesThroughBothEndsOfItsValidity() {
        AvailabilityWindow window = window(DayOfWeek.TUESDAY, "19:00", "21:00", JANUARY, JUNE);

        assertThat(window.isEffectiveOn(JANUARY.minusDays(1))).isFalse();
        assertThat(window.isEffectiveOn(JANUARY)).isTrue();
        assertThat(window.isEffectiveOn(JUNE)).isTrue();
        assertThat(window.isEffectiveOn(JUNE.plusDays(1))).isFalse();
    }

    @Test
    void aWindowClosesOnceAndKeepsTheFirstDate() {
        AvailabilityWindow window = window(DayOfWeek.TUESDAY, "19:00", "21:00", JANUARY, null);

        window.close(JUNE);

        assertThat(window.isClosed()).isTrue();
        assertThat(window.effectiveUntil()).isEqualTo(JUNE);
        assertThatThrownBy(() -> window.close(JUNE.plusMonths(1)))
                .as("moving the date would rewrite when the student's routine actually changed, "
                        + "which is the one thing this record exists to say")
                .isInstanceOf(AvailabilityAlreadyClosedException.class);
        assertThat(window.effectiveUntil()).isEqualTo(JUNE);
    }

    @Test
    void aWindowCannotBeEmptyOrInverted() {
        assertThatThrownBy(() -> window(DayOfWeek.TUESDAY, "21:00", "19:00", JANUARY, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> window(DayOfWeek.TUESDAY, "19:00", "19:00", JANUARY, null))
                .as("a window of no length is availability that does not exist")
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> window(DayOfWeek.TUESDAY, "19:00", "21:00", JUNE, JANUARY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aWindowCannotBeClosedBeforeItApplied() {
        AvailabilityWindow window = window(DayOfWeek.TUESDAY, "19:00", "21:00", JUNE, null);

        assertThatThrownBy(() -> window.close(JANUARY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(window.isClosed()).isFalse();
    }

    private static AvailabilityWindow window(DayOfWeek day, String start, String end,
            LocalDate from, LocalDate until) {

        return new AvailabilityWindow(UUID.randomUUID(), ACCOUNT, day, LocalTime.parse(start),
                LocalTime.parse(end), from, until);
    }
}
