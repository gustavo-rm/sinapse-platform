package br.com.sinapse.platform.planning.internal.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * What declaring an availability window submits.
 *
 * <p>Times carry no offset. They are local times in the account's own zone, because "Tuesday,
 * seven to nine in the evening" is a statement about a week rather than about an instant.
 *
 * @param dayOfWeek      day the window recurs on
 * @param startTime      when it opens
 * @param endTime        when it closes. Must be after the start
 * @param effectiveFrom  first day it applies, inclusive
 * @param effectiveUntil last day it applies, inclusive, or omitted for open-ended. Declaring
 *                       this is how a window that is already known to be temporary is
 *                       recorded; a window that turns out to be is closed later instead
 */
@Schema(description = "A weekly window the student is available to study in")
public record AvailabilityRequest(
        @NotNull @Schema(example = "TUESDAY") DayOfWeek dayOfWeek,

        @NotNull @Schema(example = "19:00", type = "string") LocalTime startTime,

        @NotNull @Schema(example = "21:00", type = "string") LocalTime endTime,

        @NotNull LocalDate effectiveFrom,

        LocalDate effectiveUntil) {

    /** Whether the window closes after it opens. */
    @AssertTrue(message = "endTime must be after startTime")
    @Schema(hidden = true)
    public boolean isTimeRangeOrdered() {
        return startTime == null || endTime == null || endTime.isAfter(startTime);
    }

    /** Whether the validity range ends on or after it starts. */
    @AssertTrue(message = "effectiveUntil must not precede effectiveFrom")
    @Schema(hidden = true)
    public boolean isValidityRangeOrdered() {
        return effectiveFrom == null || effectiveUntil == null
                || !effectiveUntil.isBefore(effectiveFrom);
    }
}
