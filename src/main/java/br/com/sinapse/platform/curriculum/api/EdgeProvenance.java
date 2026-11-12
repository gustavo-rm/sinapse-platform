package br.com.sinapse.platform.curriculum.api;

/**
 * Where an edge came from.
 *
 * <p>Recorded on every edge and carried into the snapshot sent to the core, which is what
 * makes the ablation experiment possible without adding instrumentation later: does the
 * algorithm improve with curated edges, with derived ones, with none? Without provenance
 * stored, that experiment does not exist.
 *
 * <p><strong>There is no {@code TEACHER} value, and none may be added.</strong> An edge
 * defined by a teacher has to be scoped to a classroom, which would make curriculum
 * reference the educational context and break rule R3. That overlay, when it exists, is a
 * table of its own in {@code educational}, merged into the global graph by the orchestration
 * layer. See ADR 0006.
 */
public enum EdgeProvenance {

    /**
     * Asserted by a human and <strong>reviewed by someone who teaches the subject</strong>. The
     * primary source, and the only value that may be read as "curated" in a result.
     *
     * <p>The catalogue importer refuses this value on an edge that cites no source, or whose
     * subject has no recorded review (ADR 0019).
     */
    CURATED,

    /**
     * Asserted by a human, <strong>not yet reviewed</strong> by someone who teaches the subject.
     *
     * <p>Exists so that an edge somebody wrote cannot carry the label of an edge somebody
     * checked. It becomes {@link #CURATED} only when a review is recorded for its subject.
     *
     * <p>Contract 1.0 with the core has no such value: the snapshot sends it as the contract's
     * {@code CURATED}, whose documented meaning is "asserted by a human curator", and the
     * distinction survives on this side only, through the catalogue import a generation job
     * points at. See {@code docs/ai/STATE.md} K35 and decision SP-17-W.
     */
    PROPOSED,

    /** Seeded from the curricular ordering of a subject. A textbook table of contents is a
     * valid topological order, even if not a minimal one. */
    TEXTBOOK_ORDER,

    /** Produced by automatic derivation, which is a later line of research and is evaluated
     * against the curated baseline rather than replacing it. */
    DERIVED
}
