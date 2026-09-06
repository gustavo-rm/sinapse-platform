package br.com.sinapse.platform.learningrecord.api;

/**
 * How well the student judged they recalled the topic, on four ordinal levels.
 *
 * <p><strong>This measures adherence and perception, not retention.</strong> ADR 0008 states
 * the limit plainly and it is scientific rather than technical: judgement of learning is
 * known to be poorly calibrated, and passive study reliably produces a stronger sense of
 * mastery than it produces recall. A system evaluated by self-report can therefore score
 * better precisely where it teaches worse. Decision D4 is open for that reason.
 *
 * <p>A short ordinal scale is deliberate. It is more reliable than a percentage or a
 * continuous scale, because it leaves less room for the respondent to reinterpret the scale
 * between one answer and the next — and a sequence of these over time is a trajectory the
 * core can use, which a single point would not be.
 *
 * <p>Nothing here computes a next review date. This module records evidence; scheduling is
 * the optimisation core's, and putting a spacing algorithm in an evidence store is how the
 * evidence starts being shaped by the schedule it is supposed to inform.
 */
public enum RecallRating {

    /** Could not recall it. */
    AGAIN,

    /** Recalled it with difficulty. */
    HARD,

    /** Recalled it. */
    GOOD,

    /** Recalled it easily. */
    EASY
}
