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

    /** Asserted by a human curator. The primary source. */
    CURATED,

    /** Seeded from the curricular ordering of a subject. A textbook table of contents is a
     * valid topological order, even if not a minimal one. */
    TEXTBOOK_ORDER,

    /** Produced by automatic derivation, which is a later line of research and is evaluated
     * against the curated baseline rather than replacing it. */
    DERIVED
}
