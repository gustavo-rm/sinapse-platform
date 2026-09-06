package br.com.sinapse.platform.curriculum.api;

/**
 * How much work a topic is, as an ordinal band.
 *
 * <p><strong>Not minutes, and deliberately so.</strong> ADR 0012 rejected a curated duration
 * for the same reason ADR 0006 rejected continuous weights on prerequisite edges: there is no
 * defensible origin for the number. Nobody can assert with any foundation that a topic takes
 * forty-seven minutes, and a curator asked for one would produce a value that is arbitrary
 * and not reproducible. A comparative judgement between bands is the kind of judgement people
 * make reliably.
 *
 * <p>The band-to-minutes mapping is configuration, read through {@code EffortTiers}, so that
 * the number lives where it can be calibrated against observed data instead of being asserted
 * in advance. The per-student adjustment is derived when the snapshot is assembled and is
 * never persisted.
 *
 * <p>The student does not set this. It is curated, alongside the topic itself.
 */
public enum EffortTier {

    /** Shortest band. */
    SHORT,

    /** The band most topics fall into. */
    STANDARD,

    /** Longer than usual. */
    LONG,

    /** Long enough that it will usually be split across sessions. */
    EXTENDED
}
