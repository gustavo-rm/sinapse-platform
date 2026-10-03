package br.com.sinapse.platform.coreclient.contract;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
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
 *                        side does not own. Which terms exist, what they are called and how
 *                        they are weighted is the core's to declare, so no key is known here
 *                        and none is dropped — a {@code null} value included
 * @param metadata        what ran, with what seed, for how long
 */
public record PlanResponse(
        String contractVersion,
        List<ScheduledSession> sessions,
        Map<String, Object> fitness,
        ExecutionMetadata metadata) {

    /**
     * Defensive copies.
     *
     * <p>{@code fitness} is not copied with {@code Map.copyOf}, which refuses a {@code null}
     * value: a key the core reports as {@code null} would make the whole response unreadable,
     * and the plan would be lost over a metric this side does not even interpret.
     */
    public PlanResponse {
        sessions = sessions == null ? List.of() : List.copyOf(sessions);
        fitness = fitness == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(fitness));
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
     * @param generations   how many generations the algorithm ran. Stored on the job: with
     *                      {@code elapsedMillis} it is the cost side of every run
     * @param elapsedMillis how long it took, as the core measured it
     */
    public record ExecutionMetadata(
            String coreVersion,
            long randomSeed,
            int generations,
            long elapsedMillis) {
    }
}
