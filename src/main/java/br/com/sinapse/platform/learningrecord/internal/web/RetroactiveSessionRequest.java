package br.com.sinapse.platform.learningrecord.internal.web;

import br.com.sinapse.platform.learningrecord.api.RecallRating;
import br.com.sinapse.platform.learningrecord.api.SessionKind;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.UUID;

/**
 * What recording a session after the fact submits.
 *
 * <p>The end is not asked for. It is the start plus the duration, so the two numbers cannot
 * contradict each other — fifty minutes of study inside a half-hour window would leave no way
 * of knowing which the student meant.
 *
 * @param topicId               topic studied
 * @param plannedSessionId      planned session executed, or omitted
 * @param kind                  new ground or going back over it
 * @param startedAt             when the student says it started. Bounded by configuration:
 *                              a history that can be composed at any length after the fact is
 *                              not evidence of anything
 * @param actualDurationMinutes how long the student says it took. Always recorded as
 *                              self-reported
 * @param recallRating          the student's judgement of their recall
 */
@Schema(description = "A study session that already happened")
public record RetroactiveSessionRequest(
        @NotNull UUID topicId,

        UUID plannedSessionId,

        @NotNull SessionKind kind,

        @NotNull Instant startedAt,

        @NotNull @Positive Integer actualDurationMinutes,

        @NotNull RecallRating recallRating) {
}
