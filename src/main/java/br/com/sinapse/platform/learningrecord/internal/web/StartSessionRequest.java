package br.com.sinapse.platform.learningrecord.internal.web;

import br.com.sinapse.platform.learningrecord.api.SessionKind;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

/**
 * What starting a session submits.
 *
 * <p>One body for both kinds of session. Supplying {@code plannedSessionId} makes it a
 * session from the plan; omitting it makes it one the student decided on. There is no flag,
 * because a flag could disagree with the identifier.
 *
 * @param topicId                topic about to be studied
 * @param plannedSessionId       planned session being executed, or omitted
 * @param kind                   new ground or going back over it
 * @param plannedDurationMinutes what the plan asked for. Supplied by the client because this
 *                               module may not read planning (rule R2); the client already
 *                               holds it, having read the agenda to know there was a session
 *                               to start
 */
@Schema(description = "A study session to start")
public record StartSessionRequest(
        @NotNull UUID topicId,

        @Schema(description = "Omit when the student is studying on their own initiative")
        UUID plannedSessionId,

        @NotNull SessionKind kind,

        @Positive Integer plannedDurationMinutes) {
}
