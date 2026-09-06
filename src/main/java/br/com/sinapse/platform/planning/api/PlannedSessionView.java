package br.com.sinapse.platform.planning.api;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * One session the optimisation core scheduled.
 *
 * <p>Immutable in the database, by trigger. Nothing edits a planned session: executed study
 * sessions reference these rows, and mutating one would corrupt the evidence that points at
 * it. A plan that no longer suits is replaced, not corrected.
 *
 * @param id              identifier. This is what an executed session carries as its
 *                        {@code plannedSessionId}, without a foreign key in either direction
 * @param planId          plan it belongs to
 * @param topicId         topic to be studied
 * @param kind            new ground or going back over it
 * @param scheduledStart  when the core placed it
 * @param durationMinutes how long it allowed for it
 * @param sequenceIndex   its position in the plan, unique within the plan
 */
public record PlannedSessionView(
        UUID id,
        UUID planId,
        UUID topicId,
        PlannedSessionKind kind,
        Instant scheduledStart,
        int durationMinutes,
        int sequenceIndex) {

    /** How long the plan allows for the session. */
    public Duration duration() {
        return Duration.ofMinutes(durationMinutes);
    }

    /** When the session is scheduled to end. */
    public Instant scheduledEnd() {
        return scheduledStart.plus(duration());
    }
}
