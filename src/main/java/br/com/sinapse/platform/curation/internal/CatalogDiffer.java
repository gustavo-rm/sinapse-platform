package br.com.sinapse.platform.curation.internal;

import br.com.sinapse.platform.curation.internal.model.DesiredCatalogue;
import br.com.sinapse.platform.curation.internal.model.DesiredEdge;
import br.com.sinapse.platform.curation.internal.model.DesiredTopic;
import br.com.sinapse.platform.curation.internal.model.TopicKey;
import br.com.sinapse.platform.curriculum.api.CurriculumCatalog;
import br.com.sinapse.platform.curriculum.api.PrerequisiteEdgeView;
import br.com.sinapse.platform.curriculum.api.PrerequisiteGraph;
import br.com.sinapse.platform.curriculum.api.SubjectView;
import br.com.sinapse.platform.curriculum.api.TopicView;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Compares the desired state against the catalogue.
 *
 * <p>Reads only through {@code curriculum.api}, like everything else here. It has to compare by
 * natural key rather than by identifier, because the files hold no identifiers — that is the
 * whole point of the natural key, and it is also what makes the comparison stable across a
 * reorder.
 *
 * <p><strong>Scoped to the subjects that were read.</strong> A run over one subject must not
 * report every edge of every other subject as removed. Edges that cross into a subject the run
 * did not read are left alone for the same reason: the file in front of us does not claim to
 * describe them.
 */
@Component
public class CatalogDiffer {

    private final CurriculumCatalog catalog;
    private final PrerequisiteGraph graph;

    /**
     * @param catalog the catalogue as it stands
     * @param graph   the prerequisite edges as they stand
     */
    public CatalogDiffer(CurriculumCatalog catalog, PrerequisiteGraph graph) {
        this.catalog = catalog;
        this.graph = graph;
    }

    /**
     * Works out what applying the files would do.
     *
     * @param desired what the files describe
     * @return the difference
     */
    @Transactional(readOnly = true)
    public CatalogDiff diff(DesiredCatalogue desired) {
        Map<String, SubjectView> subjects = new LinkedHashMap<>();
        List<String> newSubjects = new ArrayList<>();
        for (String code : desired.subjects().keySet()) {
            catalog.subjectByCode(code).ifPresentOrElse(
                    found -> subjects.put(code, found),
                    () -> newSubjects.add(code));
        }

        Map<TopicKey, TopicView> stored = storedTopics(subjects);
        Map<TopicKey, DesiredTopic> wanted = new LinkedHashMap<>();
        desired.topics().forEach(topic -> wanted.putIfAbsent(topic.key(), topic));

        List<DesiredTopic> topicsAdded = new ArrayList<>();
        List<CatalogDiff.TopicChange> topicsChanged = new ArrayList<>();
        int topicsUnchanged = 0;
        for (DesiredTopic topic : wanted.values()) {
            TopicView existing = stored.get(topic.key());
            if (existing == null) {
                topicsAdded.add(topic);
            } else if (differs(existing, topic)) {
                topicsChanged.add(new CatalogDiff.TopicChange(existing, topic));
            } else {
                topicsUnchanged++;
            }
        }
        List<TopicView> topicsMissing = stored.entrySet().stream()
                .filter(entry -> !wanted.containsKey(entry.getKey()))
                .map(Map.Entry::getValue)
                .toList();

        return edgeDiff(desired, subjects, stored, wanted, newSubjects, topicsAdded, topicsChanged,
                topicsMissing, topicsUnchanged);
    }

    private CatalogDiff edgeDiff(DesiredCatalogue desired, Map<String, SubjectView> subjects,
            Map<TopicKey, TopicView> stored, Map<TopicKey, DesiredTopic> wanted,
            List<String> newSubjects, List<DesiredTopic> topicsAdded,
            List<CatalogDiff.TopicChange> topicsChanged, List<TopicView> topicsMissing,
            int topicsUnchanged) {

        Map<UUID, TopicKey> keysById = new LinkedHashMap<>();
        stored.forEach((key, topic) -> keysById.put(topic.id(), key));

        Map<DesiredEdge.Pair, PrerequisiteEdgeView> storedEdges = new LinkedHashMap<>();
        if (!subjects.isEmpty()) {
            Set<UUID> subjectIds = subjects.values().stream()
                    .map(SubjectView::id)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            for (PrerequisiteEdgeView edge : graph.edgesTouchingSubjects(subjectIds)) {
                TopicKey from = keysById.get(edge.prerequisiteTopicId());
                TopicKey to = keysById.get(edge.dependentTopicId());
                // An edge with one end outside the subjects this run read is not ours to judge.
                if (from != null && to != null) {
                    storedEdges.put(new DesiredEdge.Pair(from, to), edge);
                }
            }
        }

        Map<DesiredEdge.Pair, DesiredEdge> wantedEdges = new LinkedHashMap<>();
        desired.edges().stream()
                .filter(edge -> wanted.containsKey(edge.prerequisite())
                        && wanted.containsKey(edge.dependent()))
                .forEach(edge -> wantedEdges.putIfAbsent(edge.pair(), edge));

        List<DesiredEdge> edgesAdded = new ArrayList<>();
        List<CatalogDiff.EdgeChange> edgesChanged = new ArrayList<>();
        int edgesUnchanged = 0;
        for (DesiredEdge edge : wantedEdges.values()) {
            PrerequisiteEdgeView existing = storedEdges.get(edge.pair());
            if (existing == null) {
                edgesAdded.add(edge);
            } else if (differs(existing, edge)) {
                edgesChanged.add(new CatalogDiff.EdgeChange(existing, edge));
            } else {
                edgesUnchanged++;
            }
        }
        List<PrerequisiteEdgeView> edgesRemoved = storedEdges.entrySet().stream()
                .filter(entry -> !wantedEdges.containsKey(entry.getKey()))
                .map(Map.Entry::getValue)
                .toList();

        return new CatalogDiff(List.copyOf(desired.subjects().keySet()), List.copyOf(newSubjects),
                List.copyOf(topicsAdded), List.copyOf(topicsChanged), topicsMissing,
                topicsUnchanged, List.copyOf(edgesAdded), List.copyOf(edgesChanged), edgesRemoved,
                edgesUnchanged, Map.copyOf(stored));
    }

    private Map<TopicKey, TopicView> storedTopics(Map<String, SubjectView> subjects) {
        Map<TopicKey, TopicView> stored = new LinkedHashMap<>();
        if (subjects.isEmpty()) {
            return stored;
        }
        Map<UUID, String> codeBySubject = new LinkedHashMap<>();
        subjects.forEach((code, subject) -> codeBySubject.put(subject.id(), code));
        for (TopicView topic : catalog.topicsOfSubjects(codeBySubject.keySet())) {
            stored.put(new TopicKey(codeBySubject.get(topic.subjectId()), topic.code()), topic);
        }
        return stored;
    }

    private static boolean differs(TopicView stored, DesiredTopic desired) {
        return !stored.name().equals(desired.name())
                || stored.position() != desired.position()
                || stored.effortTier() != desired.effortTier();
    }

    private static boolean differs(PrerequisiteEdgeView stored, DesiredEdge desired) {
        return stored.strength() != desired.strength()
                || stored.provenance() != desired.provenance()
                || !Objects.equals(blankToNull(stored.sourceReference()),
                        blankToNull(desired.sourceReference()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
