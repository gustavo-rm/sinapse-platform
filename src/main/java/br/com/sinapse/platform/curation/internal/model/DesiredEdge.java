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
 * @param declaredIn      subject whose {@code prerequisites.csv} declares the edge. Not always
 *                        the prerequisite's subject: an edge across two subjects may be written
 *                        in either file, and a message pointing at the wrong file sends the
 *                        curator to a line that does not exist
 * @param line            line of that file it came from
 */
public record DesiredEdge(
        TopicKey prerequisite,
        TopicKey dependent,
        EdgeStrength strength,
        EdgeProvenance provenance,
        String sourceReference,
        String declaredIn,
        int line) {

    /** The file the edge was read from, relative to the catalogue root. */
    public String file() {
        return declaredIn + "/prerequisites.csv";
    }

    /** Whether the edge cites a source. */
    public boolean citesASource() {
        return sourceReference != null && !sourceReference.isBlank();
    }

    /** Whether its two ends are in different subjects. */
    public boolean crossesSubjects() {
        return !prerequisite.subjectCode().equals(dependent.subjectCode());
    }

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
