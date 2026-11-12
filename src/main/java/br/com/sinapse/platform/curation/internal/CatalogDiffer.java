package br.com.sinapse.platform.curation.internal;

import br.com.sinapse.platform.curation.internal.model.CatalogProblem;
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

    /**
     * Cycles that close only through edges already in the catalogue that this run does not read.
     *
     * <p>{@link CatalogValidation} sees the files and nothing else. A run restricted with
     * {@code --subject} reads one subject, but the edges another subject's file declared — say
     * {@code B:y -> A:x} and {@code A:w -> B:z} — are in the catalogue and survive the import.
     * A new edge {@code A:x -> A:w}, valid on its own, then closes a loop the files never show,
     * and the first thing to notice would be the database trigger, which names one edge by its
     * identifiers. This combines the files with every stored edge the run leaves untouched and
     * walks the result.
     *
     * <p>Run only on files that already validate, so every cycle found goes through at least one
     * stored edge: a cycle made only of file edges was reported by the validator.
     *
     * @param desired what the files describe, already validated
     * @return the cycles, each as a problem on the first file edge of its loop
     */
    @Transactional(readOnly = true)
    public List<CatalogProblem> cyclesThroughTheCatalogue(DesiredCatalogue desired) {
        Map<UUID, String> subjectCodes = new LinkedHashMap<>();
        catalog.subjects().forEach(subject -> subjectCodes.put(subject.id(), subject.code()));
        if (subjectCodes.isEmpty()) {
            return List.of();
        }
        Map<UUID, TopicView> topics = new LinkedHashMap<>();
        catalog.topicsOfSubjects(subjectCodes.keySet()).forEach(topic -> topics.put(topic.id(), topic));

        Map<TopicKey, List<TopicKey>> combined = new LinkedHashMap<>();
        Map<DesiredEdge.Pair, DesiredEdge> fromFiles = new LinkedHashMap<>();
        Map<TopicKey, String> names = new LinkedHashMap<>();
        desired.topics().forEach(topic -> names.put(topic.key(), topic.name()));
        for (DesiredEdge edge : desired.edges()) {
            combined.computeIfAbsent(edge.prerequisite(), key -> new ArrayList<>()).add(edge.dependent());
            fromFiles.putIfAbsent(edge.pair(), edge);
        }

        for (PrerequisiteEdgeView edge : graph.edgesTouchingSubjects(subjectCodes.keySet())) {
            TopicView from = topics.get(edge.prerequisiteTopicId());
            TopicView to = topics.get(edge.dependentTopicId());
            String fromSubject = subjectCodes.get(from.subjectId());
            String toSubject = subjectCodes.get(to.subjectId());
            // Both ends in subjects this run reads: the files describe this edge, and the import
            // will keep, correct or remove it as they say.
            if (desired.subjects().containsKey(fromSubject) && desired.subjects().containsKey(toSubject)) {
                continue;
            }
            TopicKey fromKey = new TopicKey(fromSubject, from.code());
            TopicKey toKey = new TopicKey(toSubject, to.code());
            names.putIfAbsent(fromKey, from.name());
            names.putIfAbsent(toKey, to.name());
            combined.computeIfAbsent(fromKey, key -> new ArrayList<>()).add(toKey);
        }

        List<CatalogProblem> problems = new ArrayList<>();
        for (List<TopicKey> cycle : PrerequisiteCycles.in(combined)) {
            DesiredEdge anchor = null;
            List<String> stored = new ArrayList<>();
            for (int index = 0; index < cycle.size() - 1; index++) {
                DesiredEdge.Pair hop = new DesiredEdge.Pair(cycle.get(index), cycle.get(index + 1));
                DesiredEdge inFiles = fromFiles.get(hop);
                if (inFiles == null) {
                    stored.add(hop.prerequisite() + " -> " + hop.dependent());
                } else if (anchor == null) {
                    anchor = inFiles;
                }
            }
            // A loop of stored edges alone cannot exist (the trigger refused it when it was
            // written), and one of file edges alone was reported by the validator.
            if (anchor == null || stored.isEmpty()) {
                continue;
            }
            problems.add(new CatalogProblem(anchor.file(), anchor.line(),
                    ("cycle detected through the catalogue:%s%n      It closes through edge(s)"
                            + " already in the catalogue that this run does not read: %s. Run"
                            + " over every subject on the loop together (without --subject) and"
                            + " cut one edge of it").formatted(
                            PrerequisiteCycles.draw(cycle, names::get), String.join(", ", stored))));
        }
        return problems;
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
