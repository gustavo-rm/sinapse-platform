package br.com.sinapse.platform.curriculum.internal.service;

import br.com.sinapse.platform.curriculum.api.CurriculumCatalog;
import br.com.sinapse.platform.curriculum.api.SubjectView;
import br.com.sinapse.platform.curriculum.api.TopicView;
import br.com.sinapse.platform.curriculum.internal.persistence.SubjectRepository;
import br.com.sinapse.platform.curriculum.internal.persistence.TopicRepository;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads of the catalogue.
 *
 * <p>Every method is read-only and every one that could be asked about many things takes a
 * set, so that composing above this module does not turn into one query per row (rule R7).
 */
@Service
public class CurriculumCatalogService implements CurriculumCatalog {

    private final SubjectRepository subjects;
    private final TopicRepository topics;

    /**
     * @param subjects subjects
     * @param topics   topics
     */
    public CurriculumCatalogService(SubjectRepository subjects, TopicRepository topics) {
        this.subjects = subjects;
        this.topics = topics;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SubjectView> subjects() {
        return subjects.findAllByOrderByCodeAsc().stream().map(CurriculumViews::of).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SubjectView> subjectByCode(String code) {
        return subjects.findByCode(code).map(CurriculumViews::of);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TopicView> topicsOfSubjects(Collection<UUID> subjectIds) {
        if (subjectIds.isEmpty()) {
            return List.of();
        }
        return topics.findBySubjectIdInOrderBySubjectIdAscPositionAsc(subjectIds).stream()
                .map(CurriculumViews::of)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TopicView> topicsByIds(Collection<UUID> topicIds) {
        if (topicIds.isEmpty()) {
            return List.of();
        }
        return topics.findByIdInOrderBySubjectIdAscPositionAsc(topicIds).stream()
                .map(CurriculumViews::of)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, SubjectView> subjectsByIds(Collection<UUID> subjectIds) {
        if (subjectIds.isEmpty()) {
            return Map.of();
        }
        return subjects.findAllById(subjectIds).stream()
                .map(CurriculumViews::of)
                .collect(Collectors.toMap(SubjectView::id, Function.identity(),
                        (first, second) -> first, LinkedHashMap::new));
    }
}
