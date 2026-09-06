package br.com.sinapse.platform.planning.internal.web;

import br.com.sinapse.platform.planning.api.PlannedSessionKind;
import br.com.sinapse.platform.planning.api.PlannedSessionView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * A scheduled session, as the API returns it.
 *
 * @param id              identifier. This is what a client sends back as
 *                        {@code plannedSessionId} when it starts the session for real
 * @param planId          plan it belongs to
 * @param topicId         topic to be studied
 * @param kind            new ground or going back over it
 * @param scheduledStart  when the core placed it
 * @param durationMinutes how long it allowed for it
 * @param sequenceIndex   its position in the plan
 */
@Schema(description = "A session the optimisation core scheduled")
public record PlannedSessionResponse(
        UUID id,
        UUID planId,
        UUID topicId,
        PlannedSessionKind kind,
        Instant scheduledStart,
        int durationMinutes,
        int sequenceIndex) {

    /**
     * @param view planned session to render
     * @return the response body
     */
    static PlannedSessionResponse of(PlannedSessionView view) {
        return new PlannedSessionResponse(view.id(), view.planId(), view.topicId(), view.kind(),
                view.scheduledStart(), view.durationMinutes(), view.sequenceIndex());
    }
}
