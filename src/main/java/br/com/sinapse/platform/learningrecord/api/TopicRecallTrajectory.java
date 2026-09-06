package br.com.sinapse.platform.learningrecord.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The recall ratings an account gave for one topic, oldest first.
 *
 * <p>A trajectory rather than a latest value, because that is the whole reason ADR 0008
 * accepted a self-reported rating at all: a sequence over time is a signal the optimisation
 * core can use to modulate spacing, where a single point would be little more than a mood.
 *
 * <p>What it is not: a retention measure. The ratings are the student's own judgement, and
 * judgement of learning is poorly calibrated — see {@link RecallRating}. Nothing in this
 * module derives a next review date from a trajectory, and nothing should.
 *
 * @param topicId topic the ratings are about
 * @param points  the ratings in the order they were given
 */
public record TopicRecallTrajectory(UUID topicId, List<Point> points) {

    /** Defensive copy: a trajectory that changed under the caller would not be a record. */
    public TopicRecallTrajectory {
        points = List.copyOf(points);
    }

    /**
     * One rating at one instant.
     *
     * @param at     when the session that produced it closed
     * @param rating what the student judged
     */
    public record Point(Instant at, RecallRating rating) {
    }
}
