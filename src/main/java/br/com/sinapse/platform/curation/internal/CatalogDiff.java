package br.com.sinapse.platform.curation.internal;

import br.com.sinapse.platform.curation.internal.model.DesiredEdge;
import br.com.sinapse.platform.curation.internal.model.DesiredTopic;
import br.com.sinapse.platform.curation.internal.model.TopicKey;
import br.com.sinapse.platform.curriculum.api.PrerequisiteEdgeView;
import br.com.sinapse.platform.curriculum.api.TopicView;
import java.util.List;
import java.util.Map;

/**
 * What applying the files would change.
 *
 * <p>Read by a curator, not by a developer, which decides everything about how it prints:
 * grouped by subject, topics named by code and by name, counts stated plainly, and no entity
 * class ever mentioned.
 *
 * @param subjectsRead   subject codes the files described, in order
 * @param newSubjects    subjects that do not exist yet
 * @param topicsAdded    topics in the files and not in the catalogue
 * @param topicsChanged  topics whose name, position or effort band differs
 * @param topicsMissing  topics in the catalogue and not in the files
 * @param topicsUnchanged how many matched exactly
 * @param edgesAdded     edges in the files and not in the catalogue
 * @param edgesChanged   edges whose strength, provenance or citation differs
 * @param edgesRemoved   edges in the catalogue and not in the files
 * @param edgesUnchanged how many matched exactly
 */
public record CatalogDiff(
        List<String> subjectsRead,
        List<String> newSubjects,
        List<DesiredTopic> topicsAdded,
        List<TopicChange> topicsChanged,
        List<TopicView> topicsMissing,
        int topicsUnchanged,
        List<DesiredEdge> edgesAdded,
        List<EdgeChange> edgesChanged,
        List<PrerequisiteEdgeView> edgesRemoved,
        int edgesUnchanged,
        Map<TopicKey, TopicView> stored) {

    /** Whether applying this would change nothing at all. */
    public boolean isEmpty() {
        return newSubjects.isEmpty() && topicsAdded.isEmpty() && topicsChanged.isEmpty()
                && edgesAdded.isEmpty() && edgesChanged.isEmpty() && edgesRemoved.isEmpty();
    }

    /**
     * A topic whose definition moved.
     *
     * @param stored  what the catalogue holds
     * @param desired what the file says
     */
    public record TopicChange(TopicView stored, DesiredTopic desired) {
    }

    /**
     * An edge whose attributes moved. The endpoints never move: a different pair is a different
     * edge, added and removed rather than changed.
     *
     * @param stored  what the catalogue holds
     * @param desired what the file says
     */
    public record EdgeChange(PrerequisiteEdgeView stored, DesiredEdge desired) {
    }
}
