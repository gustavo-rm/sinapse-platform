package br.com.sinapse.platform.planning.internal.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

/**
 * What setting a goal submits.
 *
 * <p>A subject, and never a list of topics. Every topic of the subject is in scope and the
 * optimisation core decides the order and what fits the horizon — decision L3.
 *
 * @param subjectId  subject in the curriculum catalogue
 * @param targetDate when the student would like to be done, or omitted. A prioritisation
 *                   constraint that enters the snapshot as pressure, <strong>not</strong> the
 *                   plan horizon, which is configured (decision F3)
 * @param priority   1 to 5, higher meaning more pressing. Omitted means 3
 */
@Schema(description = "A subject the student intends to get through")
public record GoalRequest(
        @NotNull UUID subjectId,

        LocalDate targetDate,

        @Min(1) @Max(5) @Schema(defaultValue = "3") Integer priority) {

    /** Priority the student asked for, or the middle of the scale when they did not. */
    public int priorityOrDefault() {
        return priority == null ? 3 : priority;
    }
}
