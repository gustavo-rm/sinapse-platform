package br.com.sinapse.platform.learningrecord.internal.web;

import br.com.sinapse.platform.learningrecord.api.RecallRating;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * What closing a session submits.
 *
 * @param recallRating          the student's judgement of their recall. Required: it is the
 *                              only learning signal the v1 captures at all
 * @param actualDurationMinutes duration the student states, when the timer did not reflect
 *                              what happened. Omitted means the measured duration is used,
 *                              which is the main path of decision F5. Supplying it marks the
 *                              record as self-reported, and there is no way to supply a
 *                              duration without that mark
 */
@Schema(description = "The close of a running session")
public record CompleteSessionRequest(
        @NotNull RecallRating recallRating,

        @Schema(description = "Omit to use the duration the application measured")
        @Positive Integer actualDurationMinutes) {
}
