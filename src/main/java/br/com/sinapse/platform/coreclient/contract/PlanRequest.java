package br.com.sinapse.platform.coreclient.contract;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Everything the optimiser is told about a student, in one document.
 *
 * <p><strong>This is the snapshot.</strong> The same value that goes over the wire is what the
 * generation job stores, and storing it is what makes a plan reproducible: availability and
 * history will have moved by tomorrow, so a plan that is only explained by "the student's
 * state" is explained by nothing (ADR 0007).
 *
 * <p>Four things together make a run repeatable, and only two of them are here: this document
 * and {@link #algorithmParams} and {@link #randomSeed}. The fourth is the core's own version,
 * which comes back in the response. A genetic algorithm is stochastic, so the same snapshot
 * with the same parameters produces a different plan unless the seed is fixed — and the seed
 * is chosen by the backend and sent, never chosen by the core, because the party that has to
 * be able to replay a run is the one that keeps the record of it.
 *
 * @param contractVersion version of this contract the payload was written against, so that a
 *                        core which does not understand it can refuse rather than misread it
 * @param horizon         the stretch of calendar being planned. Four weeks by configuration,
 *                        and never derived from a goal's target date: that date is pressure,
 *                        not a horizon (decision F3)
 * @param availability    the concrete intervals of the horizon in which the student can
 *                        study, already resolved from their weekly routine
 * @param goals           the subjects being pursued, with their deadlines and priorities
 * @param topics          every topic in scope, with the effort estimated for this student
 * @param prerequisites   the edges among those topics, with strength and provenance
 * @param history         what the student has already studied, per topic
 * @param algorithmParams parameters the run is to use, as configured by the backend
 * @param randomSeed      seed of the pseudo-random generator, chosen by the backend
 */
public record PlanRequest(
        String contractVersion,
        Horizon horizon,
        List<AvailabilitySlot> availability,
        List<Goal> goals,
        List<Topic> topics,
        List<PrerequisiteEdge> prerequisites,
        List<TopicHistory> history,
        Map<String, Object> algorithmParams,
        long randomSeed) {

    /** The version this contract is at. A change to any shape here changes this string. */
    public static final String VERSION = "1.0";

    /** Defensive copies: a snapshot that changed after being taken would not be a snapshot. */
    public PlanRequest {
        availability = List.copyOf(availability);
        goals = List.copyOf(goals);
        topics = List.copyOf(topics);
        prerequisites = List.copyOf(prerequisites);
        history = List.copyOf(history);
        algorithmParams = Map.copyOf(algorithmParams);
    }

    /**
     * The stretch of calendar being planned.
     *
     * @param start first day, inclusive
     * @param end   last day
     */
    public record Horizon(LocalDate start, LocalDate end) {
    }

    /**
     * One concrete interval, inside the horizon, in which the student can study.
     *
     * <p><strong>Already expanded.</strong> What the student declared is a recurring weekly
     * window in their own zone — "Tuesday, seven to nine in the evening" — and the backend
     * turns those into the actual intervals of the horizon before sending them.
     *
     * <p>Two reasons, and neither is payload size. The party that knows the student's time zone
     * is the one that holds their account, so it is the party that should resolve a local time
     * into an instant, daylight saving and all; sending a weekday and a wall-clock time would
     * make the optimiser do calendar arithmetic on data it would have to be told how to read.
     * And it makes the snapshot answer "why was this plan built this way" on its own, which is
     * the product requirement ADR 0002 gives for storing it: the intervals it was built from
     * are written down, not reconstructed.
     *
     * @param start when the interval opens
     * @param end   when it closes
     */
    public record AvailabilitySlot(Instant start, Instant end) {
    }

    /**
     * A subject the student intends to get through.
     *
     * <p>Every topic of the subject is in scope with it. There is no exclusion list, by
     * decision L3: the core decides the order and what fits.
     *
     * @param subjectId  subject
     * @param targetDate when the student would like to be done, or {@code null}. A
     *                   prioritisation pressure and never the horizon
     * @param priority   1 to 5, higher meaning more pressing
     */
    public record Goal(UUID subjectId, LocalDate targetDate, int priority) {
    }

    /**
     * A topic in scope, with how long it is expected to take this student.
     *
     * <p>The minutes are not a curated number. A curator judges an ordinal band; the band is
     * mapped to minutes by configuration, and the mapping is then scaled by what this student
     * has actually needed compared to what was planned for them (decision L1). A student with
     * no history gets the unadjusted band value, and the band is sent alongside the minutes so
     * that the core can tell a calibrated estimate from a default one.
     *
     * @param id              topic
     * @param subjectId       subject it belongs to
     * @param position        its curricular position within the subject
     * @param effortTier      the ordinal band a curator judged
     * @param estimatedMinutes what that band means for this student, in minutes
     */
    public record Topic(
            UUID id,
            UUID subjectId,
            int position,
            String effortTier,
            int estimatedMinutes) {
    }

    /**
     * A directed prerequisite between two topics.
     *
     * <p>{@code prerequisiteTopicId} is studied before {@code dependentTopicId}.
     *
     * @param prerequisiteTopicId topic that comes first
     * @param dependentTopicId    topic that depends on it
     * @param strength            whether the edge is a constraint or a preference
     * @param provenance          where the edge came from
     */
    public record PrerequisiteEdge(
            UUID prerequisiteTopicId,
            UUID dependentTopicId,
            EdgeStrength strength,
            EdgeProvenance provenance) {
    }

    /**
     * What the student has already done on one topic.
     *
     * @param topicId        topic
     * @param sessionCount   how many closed sessions with a recorded duration it has
     * @param totalMinutes   how much effective time went into it
     * @param lastStudiedAt  when the most recent of those sessions ended, or {@code null}
     * @param recallRatings  the student's judgements over time, oldest first
     */
    public record TopicHistory(
            UUID topicId,
            int sessionCount,
            long totalMinutes,
            Instant lastStudiedAt,
            List<RecallRating> recallRatings) {

        /** Defensive copy. */
        public TopicHistory {
            recallRatings = List.copyOf(recallRatings);
        }
    }
}
