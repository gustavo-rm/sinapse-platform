package br.com.sinapse.platform.planning.internal.web;

import br.com.sinapse.platform.planning.api.AvailabilityWindowView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * A weekly availability window, as the API returns it.
 *
 * @param id             identifier
 * @param dayOfWeek      day it recurs on
 * @param startTime      when it opens, in the account's zone
 * @param endTime        when it closes, in the account's zone
 * @param effectiveFrom  first day it applies, inclusive
 * @param effectiveUntil last day it applies, inclusive, or {@code null} while open-ended
 */
@Schema(description = "A weekly window the student is available to study in")
public record AvailabilityResponse(
        UUID id,
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        LocalDate effectiveFrom,
        LocalDate effectiveUntil) {

    /**
     * @param view window to render
     * @return the response body
     */
    static AvailabilityResponse of(AvailabilityWindowView view) {
        return new AvailabilityResponse(view.id(), view.dayOfWeek(), view.startTime(),
                view.endTime(), view.effectiveFrom(), view.effectiveUntil());
    }
}
