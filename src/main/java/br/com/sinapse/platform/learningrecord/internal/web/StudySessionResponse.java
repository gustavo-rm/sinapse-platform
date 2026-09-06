package br.com.sinapse.platform.learningrecord.internal.web;

import br.com.sinapse.platform.learningrecord.api.DurationSource;
import br.com.sinapse.platform.learningrecord.api.RecallRating;
import br.com.sinapse.platform.learningrecord.api.SessionKind;
import br.com.sinapse.platform.learningrecord.api.SessionSource;
import br.com.sinapse.platform.learningrecord.api.SessionStatus;
import br.com.sinapse.platform.learningrecord.api.StudySessionView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * A study session, as the API returns it.
 *
 * <p>Identical in shape whether the session came from the plan or the student decided on it.
 * A client that wants to tell them apart reads {@code source}.
 *
 * @param id                     identifier
 * @param topicId                topic studied
 * @param plannedSessionId       planned session executed, or {@code null}
 * @param kind                   new ground or going back over it
 * @param source                 from the plan or self-directed
 * @param status                 where the session is in its life
 * @param startedAt              when it started
 * @param endedAt                when it closed, or {@code null} while running
 * @param plannedDurationMinutes what the plan asked for, or {@code null}
 * @param actualDurationMinutes  what it took, or {@code null} while running
 * @param durationSource         whether that number was measured or stated by the student
 * @param recallRating           the student's judgement, only on a completed session
 */
@Schema(description = "An executed study session")
public record StudySessionResponse(
        UUID id,
        UUID topicId,
        UUID plannedSessionId,
        SessionKind kind,
        SessionSource source,
        SessionStatus status,
        Instant startedAt,
        Instant endedAt,
        Integer plannedDurationMinutes,
        Integer actualDurationMinutes,
        DurationSource durationSource,
        RecallRating recallRating) {

    /**
     * Renders a view.
     *
     * <p>The account is left out. Every route of this module answers about the caller, so the
     * field would carry the reader's own identifier back to them and nothing else.
     *
     * @param view session to render
     * @return the response body
     */
    static StudySessionResponse of(StudySessionView view) {
        return new StudySessionResponse(view.id(), view.topicId(), view.plannedSessionId(),
                view.kind(), view.source(), view.status(), view.startedAt(), view.endedAt(),
                view.plannedDurationMinutes(), view.actualDurationMinutes(), view.durationSource(),
                view.recallRating());
    }
}
