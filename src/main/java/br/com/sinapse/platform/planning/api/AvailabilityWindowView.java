package br.com.sinapse.platform.planning.api;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * One recurring weekly window in which the student is available to study.
 *
 * <p>Times are local and are interpreted in the account's own zone. They travel as
 * {@code HH:mm} and never carry an offset: "Tuesday, seven to nine in the evening" is a
 * statement about the student's week, not about an instant, and attaching an offset to it
 * would make it wrong twice a year.
 *
 * <p>The validity range is what keeps a change of routine from destroying the availability a
 * past plan was generated against. Editing a window means closing it and opening another, so
 * a window that no longer applies still says what it said while it did.
 *
 * @param id             identifier
 * @param dayOfWeek      day the window recurs on
 * @param startTime      when it opens, in the account's zone
 * @param endTime        when it closes, in the account's zone. Always after the start
 * @param effectiveFrom  first day the window applies, inclusive
 * @param effectiveUntil last day it applies, inclusive, or {@code null} while it is open-ended
 */
public record AvailabilityWindowView(
        UUID id,
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        LocalDate effectiveFrom,
        LocalDate effectiveUntil) {

    /**
     * Whether the window applies on a given day.
     *
     * <p>Both ends of the validity range are inclusive: a window closed on the tenth still
     * applied on the tenth. Only the range is considered — a date that falls on another
     * weekday is still within the window's validity, it simply has no occurrence that week.
     *
     * @param date day being asked about
     * @return whether the window was in force
     */
    public boolean isEffectiveOn(LocalDate date) {
        return !date.isBefore(effectiveFrom)
                && (effectiveUntil == null || !date.isAfter(effectiveUntil));
    }

    /** Whether the window has been closed. */
    public boolean isClosed() {
        return effectiveUntil != null;
    }
}
