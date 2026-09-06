package br.com.sinapse.platform.curriculum.internal.service;

import br.com.sinapse.platform.curriculum.api.PrerequisiteEdgeView;
import br.com.sinapse.platform.curriculum.api.SubjectView;
import br.com.sinapse.platform.curriculum.api.TopicView;
import br.com.sinapse.platform.curriculum.internal.domain.Subject;
import br.com.sinapse.platform.curriculum.internal.domain.Topic;
import br.com.sinapse.platform.curriculum.internal.domain.TopicPrerequisite;

/**
 * The one place an entity becomes a DTO.
 *
 * <p>Kept in one place so that nothing published from this module is a managed entity by
 * accident. A caller handed an entity can navigate wherever the mapping allows, load whatever
 * a lazy association points at, and write to it — at which point the boundary is decoration.
 */
public final class CurriculumViews {

    private CurriculumViews() {
    }

    /**
     * @param subject entity
     * @return its published form
     */
    public static SubjectView of(Subject subject) {
        return new SubjectView(subject.id(), subject.code(), subject.name());
    }

    /**
     * @param topic entity
     * @return its published form
     */
    public static TopicView of(Topic topic) {
        return new TopicView(topic.id(), topic.subjectId(), topic.code(), topic.name(),
                topic.position(), topic.effortTier());
    }

    /**
     * @param edge entity
     * @return its published form
     */
    public static PrerequisiteEdgeView of(TopicPrerequisite edge) {
        return new PrerequisiteEdgeView(edge.id(), edge.prerequisiteTopicId(), edge.dependentTopicId(),
                edge.strength(), edge.provenance(), edge.sourceReference());
    }
}
