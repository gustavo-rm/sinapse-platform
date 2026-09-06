package br.com.sinapse.platform.curriculum.internal.service;

import br.com.sinapse.platform.curriculum.api.PrerequisiteEdgeView;
import br.com.sinapse.platform.curriculum.api.PrerequisiteGraph;
import br.com.sinapse.platform.curriculum.api.TopicView;
import br.com.sinapse.platform.curriculum.internal.domain.Topic;
import br.com.sinapse.platform.curriculum.internal.domain.TopicPrerequisite;
import br.com.sinapse.platform.curriculum.internal.persistence.TopicPrerequisiteRepository;
import br.com.sinapse.platform.curriculum.internal.persistence.TopicRepository;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The graph queries the orchestration layer builds a snapshot from.
 */
@Service
public class PrerequisiteGraphService implements PrerequisiteGraph {

    private final TopicRepository topics;
    private final TopicPrerequisiteRepository edges;

    /**
     * @param topics topics
     * @param edges  prerequisite edges
     */
    public PrerequisiteGraphService(TopicRepository topics, TopicPrerequisiteRepository edges) {
        this.topics = topics;
        this.edges = edges;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PrerequisiteEdgeView> edgesTouchingSubjects(Collection<UUID> subjectIds) {
        if (subjectIds.isEmpty()) {
            return List.of();
        }
        List<UUID> topicIds = topics.findBySubjectIdInOrderBySubjectIdAscPositionAsc(subjectIds).stream()
                .map(Topic::id)
                .toList();
        if (topicIds.isEmpty()) {
            return List.of();
        }
        return edges.findTouching(topicIds).stream().map(CurriculumViews::of).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TopicView> directPrerequisitesOf(UUID topicId) {
        List<UUID> prerequisiteIds = edges.findByDependentTopicId(topicId).stream()
                .map(TopicPrerequisite::prerequisiteTopicId)
                .toList();
        if (prerequisiteIds.isEmpty()) {
            return List.of();
        }
        return topics.findByIdInOrderBySubjectIdAscPositionAsc(prerequisiteIds).stream()
                .map(CurriculumViews::of)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TopicView> topologicalOrder(Collection<UUID> topicIds) {
        if (topicIds.isEmpty()) {
            return List.of();
        }
        Set<UUID> requested = Set.copyOf(topicIds);
        List<TopicView> found = topics.findByIdInOrderBySubjectIdAscPositionAsc(requested).stream()
                .map(CurriculumViews::of)
                .toList();

        List<TopologicalOrder.Edge> within = edges.findWithin(requested).stream()
                .map(edge -> new TopologicalOrder.Edge(edge.prerequisiteTopicId(), edge.dependentTopicId()))
                .toList();

        return TopologicalOrder.of(found, within);
    }
}
