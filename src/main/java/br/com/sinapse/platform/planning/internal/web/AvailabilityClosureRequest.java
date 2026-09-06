package br.com.sinapse.platform.planning.internal.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * What closing an availability window submits.
 *
 * @param effectiveUntil last day the window applies, inclusive. May be in the past: a student
 *                       saying their Tuesday evening stopped last month is stating what
 *                       happened, and the window keeps saying what it said for the period it
 *                       covered
 */
@Schema(description = "The day an availability window stops applying")
public record AvailabilityClosureRequest(@NotNull LocalDate effectiveUntil) {
}
