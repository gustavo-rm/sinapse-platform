package br.com.sinapse.platform.planning.orchestration;

import br.com.sinapse.platform.learningrecord.api.StudySessionView;
import java.util.List;

/**
 * How long this student actually needs, compared to how long the plan allowed.
 *
 * <p>Decision L1. A curator judges an ordinal band and configuration maps the band to minutes;
 * neither knows anything about a particular student. The ratio between what was planned and
 * what was spent, across the sessions a student has completed, is the one piece of evidence the
 * platform has about that, and it costs one pass over data the snapshot assembly is already
 * reading.
 *
 * <p><strong>Derived at assembly and never persisted.</strong> It goes stale the moment the
 * student studies again, so a stored copy would need a table and a refresh job to be wrong less
 * often than it was right.
 *
 * <p>Only sessions that have both numbers count: a planned duration and a recorded one. A
 * self-directed session has no planned duration and says nothing about the estimate; an
 * abandoned one has no recorded duration and says nothing about how long the topic takes.
 *
 * <p><strong>Two guards, and both are judgement rather than finding.</strong> Below a
 * configured number of sessions there is no adjustment at all, because a ratio computed from
 * one session is not a measurement; and the factor is clamped, because an unbounded ratio lets
 * a handful of unusual sessions rewrite every estimate in the plan. A student who logs one
 * five-minute session against a fifty-minute slot would otherwise have their whole plan built
 * from ten-minute topics.
 */
final class EffortCalibration {

    /** What a student with too little history gets: the configured band value, unchanged. */
    static final double NEUTRAL = 1.0;

    private EffortCalibration() {
    }

    /**
     * The factor to scale planned durations by for this student.
     *
     * @param sessions    the student's sessions over the configured history window
     * @param minSessions how many usable sessions are needed before adjusting at all
     * @param minFactor   floor on the factor
     * @param maxFactor   ceiling on the factor
     * @return the factor, or {@link #NEUTRAL} when there is not enough to go on
     */
    static double factorOf(List<StudySessionView> sessions, int minSessions, double minFactor,
            double maxFactor) {

        long planned = 0;
        long actual = 0;
        int usable = 0;

        for (StudySessionView session : sessions) {
            Integer plannedMinutes = session.plannedDurationMinutes();
            Integer actualMinutes = session.actualDurationMinutes();
            if (plannedMinutes == null || plannedMinutes <= 0 || actualMinutes == null) {
                continue;
            }
            planned += plannedMinutes;
            actual += actualMinutes;
            usable++;
        }

        if (usable < minSessions || planned == 0) {
            return NEUTRAL;
        }
        return Math.clamp((double) actual / planned, minFactor, maxFactor);
    }
}
