package br.com.sinapse.platform.coreclient.contract;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What the optimiser produces.
 *
 * <p>The schedule, how good the core judged it, and enough about the run to attribute the
 * result to it. {@link ExecutionMetadata#coreVersion()} is the fourth of the four things a
 * plan needs to be reproducible; the other three were sent.
 *
 * @param contractVersion version of the contract the core answered with
 * @param sessions        the schedule, in the order the core sequenced it
 * @param fitness         the metrics the core judged the plan by, as it reported them. Not
 *                        interpreted by the backend: what they mean belongs to the core, and
 *                        normalising them here would create a second copy of a model this
 *                        side does not own
 * @param metadata        what ran, with what seed, for how long
 */
public record PlanResponse(
        String contractVersion,
        List<ScheduledSession> sessions,
        Map<String, Object> fitness,
        ExecutionMetadata metadata) {

    /** Defensive copies. */
    public PlanResponse {
        sessions = sessions == null ? List.of() : List.copyOf(sessions);
        fitness = fitness == null ? Map.of() : Map.copyOf(fitness);
    }

    /**
     * One session the core placed on the calendar.
     *
     * @param topicId         topic to be studied
     * @param kind            new ground or going back over it
     * @param scheduledStart  when it starts
     * @param durationMinutes how long it allows
     * @param sequenceIndex   its position in the plan, unique within the plan
     */
    public record ScheduledSession(
            UUID topicId,
            SessionKind kind,
            Instant scheduledStart,
            int durationMinutes,
            int sequenceIndex) {
    }

    /**
     * What the run was.
     *
     * @param coreVersion   version of the optimiser that produced the plan
     * @param randomSeed    seed it ran with, echoed back so that the record can be checked
     *                      against what was sent rather than assumed
     * @param generations   how many generations the algorithm ran
     * @param elapsedMillis how long it took
     */
    public record ExecutionMetadata(
            String coreVersion,
            long randomSeed,
            int generations,
            long elapsedMillis) {
    }
}
