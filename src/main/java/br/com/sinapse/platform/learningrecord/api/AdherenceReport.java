package br.com.sinapse.platform.learningrecord.api;

import java.util.OptionalDouble;

/**
 * How many of the planned sessions put to this module were actually completed.
 *
 * <p><strong>Which sessions count is the caller's decision, not this module's.</strong> This
 * module does not know what a plan is (rule R2), so it cannot know how many sessions were
 * scheduled, nor which of them have already fallen due. The caller — planning, or the
 * orchestration above it — supplies the identifiers of the planned sessions that are due,
 * and this module answers how many carry a completed execution. That division is what keeps
 * "a session still in the future does not count as missed" a planning rule, decided where
 * the schedule is known, instead of a date comparison guessed at here.
 *
 * <p>Abandoned sessions count as not executed. The student opened the session and did not
 * finish it, and calling that adherence would make the measure agree with itself rather than
 * with what happened.
 *
 * @param planned  planned sessions the caller asked about, all of them already due
 * @param executed how many of those were completed
 */
public record AdherenceReport(int planned, int executed) {

    /** Rejects a report that could not have come from counting. */
    public AdherenceReport {
        if (planned < 0 || executed < 0 || executed > planned) {
            throw new IllegalArgumentException("adherence counts are inconsistent");
        }
    }

    /** Planned sessions with no completed execution. */
    public int notExecuted() {
        return planned - executed;
    }

    /**
     * Executed over planned.
     *
     * <p>Empty when nothing was planned. There is no ratio to report in that case, and both
     * available shortcuts lie: zero reads as a student who followed nothing, one as a student
     * with a perfect record. A teacher's panel showing either would be showing an assertion
     * the data does not make.
     *
     * @return the ratio between 0 and 1, or empty when nothing was planned
     */
    public OptionalDouble ratio() {
        return planned == 0 ? OptionalDouble.empty() : OptionalDouble.of((double) executed / planned);
    }
}
