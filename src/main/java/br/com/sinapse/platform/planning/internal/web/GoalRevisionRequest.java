package br.com.sinapse.platform.planning.internal.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * What revising a goal submits.
 *
 * <p>The subject is not here. A goal about another subject is another goal, and repointing one
 * would move the record of an abandoned subject onto a new one.
 *
 * @param targetDate new target date, or omitted to drop it. Omitting it clears the date rather
 *                   than leaving it alone: this replaces what the goal asks for, so a field
 *                   left out is a field the student no longer wants
 * @param priority   new priority, 1 to 5
 */
@Schema(description = "What an existing goal asks for")
public record GoalRevisionRequest(
        LocalDate targetDate,

        @NotNull @Min(1) @Max(5) Integer priority) {
}
