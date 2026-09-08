package br.com.sinapse.platform.curation.internal.model;

import br.com.sinapse.platform.curriculum.api.EdgeProvenance;
import br.com.sinapse.platform.curriculum.api.EdgeStrength;

/**
 * A prerequisite edge as the file describes it.
 *
 * @param prerequisite    topic that comes first
 * @param dependent       topic that depends on it
 * @param strength        constraint or penalty
 * @param provenance      where the claim came from, which is what lets the ablation tell a
 *                        curated edge from one seeded off a table of contents
 * @param sourceReference citation, or empty
 * @param line            line of {@code prerequisites.csv} it came from
 */
public record DesiredEdge(
        TopicKey prerequisite,
        TopicKey dependent,
        EdgeStrength strength,
        EdgeProvenance provenance,
        String sourceReference,
        int line) {

    /** The unordered-pair identity of this edge: two edges with the same pair are the same edge. */
    public Pair pair() {
        return new Pair(prerequisite, dependent);
    }

    /**
     * The directed pair an edge is identified by.
     *
     * @param prerequisite topic that comes first
     * @param dependent    topic that depends on it
     */
    public record Pair(TopicKey prerequisite, TopicKey dependent) {
    }
}
