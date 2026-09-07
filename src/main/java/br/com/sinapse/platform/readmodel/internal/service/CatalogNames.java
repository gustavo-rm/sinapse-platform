package br.com.sinapse.platform.readmodel.internal.service;

import br.com.sinapse.platform.curriculum.api.CurriculumCatalog;
import br.com.sinapse.platform.curriculum.api.SubjectView;
import br.com.sinapse.platform.curriculum.api.TopicView;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Names for a set of topics, and for the subjects those topics belong to.
 *
 * <p>Two queries, whatever the size of the set, and that is the whole reason this exists.
 * Every read model here holds topic identifiers and has to render topic and subject names next
 * to them; resolving each one where it is needed would be the N+1 that section 4 of the API
 * contract predicts — a day's agenda of forty sessions making forty catalogue lookups.
 *
 * <p>A topic that is not in the catalogue is absent rather than raising. The read models hold
 * identifiers taken from planned and executed sessions, and a topic can be withdrawn from the
 * catalogue after a session referenced it; the honest answer is a row without a name, not a
 * screen that fails to load because of a curation decision taken months later.
 */
@Component
public class CatalogNames {

    private final CurriculumCatalog catalog;

    /**
     * @param catalog the curated catalogue
     */
    public CatalogNames(CurriculumCatalog catalog) {
        this.catalog = catalog;
    }

    /**
     * Resolves a set of topics and their subjects.
     *
     * @param topicIds topics to name
     * @return the resolution, empty when nothing was asked for
     */
    public Resolved of(Collection<UUID> topicIds) {
        Set<UUID> distinct = new LinkedHashSet<>(topicIds);
        if (distinct.isEmpty()) {
            return new Resolved(Map.of(), Map.of());
        }
        Map<UUID, TopicView> topics = catalog.topicsByIds(distinct).stream()
                .collect(Collectors.toMap(TopicView::id, Function.identity(),
                        (first, second) -> first, LinkedHashMap::new));

        Set<UUID> subjectIds = topics.values().stream()
                .map(TopicView::subjectId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        return new Resolved(topics, catalog.subjectsByIds(subjectIds));
    }

    /**
     * What the catalogue answered.
     *
     * @param topics   topics by identifier, missing ones absent
     * @param subjects subjects by identifier, for the subjects those topics belong to
     */
    public record Resolved(Map<UUID, TopicView> topics, Map<UUID, SubjectView> subjects) {

        /** Copies both maps, so a resolution cannot change under a caller reading it. */
        public Resolved {
            topics = Map.copyOf(topics);
            subjects = Map.copyOf(subjects);
        }

        /**
         * @param topicId topic
         * @return its name, or {@code null} if it is no longer in the catalogue
         */
        public String topicName(UUID topicId) {
            return topic(topicId).map(TopicView::name).orElse(null);
        }

        /**
         * @param topicId topic
         * @return the subject it belongs to, or {@code null} if the topic is unknown
         */
        public UUID subjectIdOf(UUID topicId) {
            return topic(topicId).map(TopicView::subjectId).orElse(null);
        }

        /**
         * @param topicId topic
         * @return the name of the subject it belongs to, or {@code null}
         */
        public String subjectNameOf(UUID topicId) {
            return topic(topicId)
                    .map(TopicView::subjectId)
                    .map(subjects::get)
                    .map(SubjectView::name)
                    .orElse(null);
        }

        /**
         * @param subjectId subject
         * @return its name, or {@code null} if it is unknown
         */
        public String subjectName(UUID subjectId) {
            return Optional.ofNullable(subjects.get(subjectId)).map(SubjectView::name).orElse(null);
        }

        private Optional<TopicView> topic(UUID topicId) {
            return Optional.ofNullable(topics.get(topicId));
        }
    }
}
