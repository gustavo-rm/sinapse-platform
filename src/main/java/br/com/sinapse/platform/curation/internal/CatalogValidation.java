package br.com.sinapse.platform.curation.internal;

import br.com.sinapse.platform.curation.internal.model.CatalogProblem;
import br.com.sinapse.platform.curation.internal.model.DesiredCatalogue;
import br.com.sinapse.platform.curation.internal.model.DesiredEdge;
import br.com.sinapse.platform.curation.internal.model.DesiredTopic;
import br.com.sinapse.platform.curation.internal.model.TopicKey;
import br.com.sinapse.platform.curriculum.api.EdgeProvenance;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Everything wrong with a catalogue, in one pass.
 *
 * <p>This is where the value of the whole tool is. The database has an acyclicity trigger and it
 * is not going anywhere — but it raises on the first offending edge and says which one, which
 * tells a curator nothing about where to cut. A cycle is a property of a path, and the only
 * useful report of one is the path itself.
 *
 * <p>Reporting everything at once matters for the same reason. A curated subject is hundreds of
 * rows exported from a spreadsheet, and a validator that stops at the first error turns fixing
 * it into forty runs. The curator who has to do that stops running the validator, and then the
 * trigger is the only thing left — which is exactly the situation this exists to avoid.
 *
 * <p><strong>Errors and warnings are kept apart.</strong> {@link #validate} returns what stops an
 * import; {@link #warnings} returns what a curator should look at and may legitimately leave as
 * it is. Mixing the two would either block a correct catalogue or teach curators to ignore the
 * list.
 */
public final class CatalogValidation {

    private CatalogValidation() {
    }

    /**
     * Validates a desired state.
     *
     * @param desired what the files describe
     * @return every problem found, the ones the reader already found included, in file order
     */
    public static List<CatalogProblem> validate(DesiredCatalogue desired) {
        List<CatalogProblem> problems = new ArrayList<>(desired.problems());

        Map<TopicKey, DesiredTopic> known = new LinkedHashMap<>();
        checkTopics(desired, known, problems);
        checkPositions(desired, known, problems);
        checkEdges(desired, known, problems);
        checkProvenance(desired, problems);
        checkCycles(desired, known, problems);

        return sorted(problems);
    }

    /**
     * What a curator should see and may leave as it is.
     *
     * <p>Today one thing: a topic with no edge at all, in or out. It can be legitimate — a topic
     * that really depends on nothing and that nothing builds on — but in a graph that is being
     * curated it is far more often a row somebody forgot to connect, and the import cannot tell
     * the two apart. Only an edge between two known, distinct topics counts.
     *
     * @param desired what the files describe
     * @return the warnings, in file order
     */
    public static List<CatalogProblem> warnings(DesiredCatalogue desired) {
        Set<TopicKey> known = new HashSet<>();
        desired.topics().forEach(topic -> known.add(topic.key()));
        Set<TopicKey> connected = new HashSet<>();
        for (DesiredEdge edge : desired.edges()) {
            if (known.contains(edge.prerequisite()) && known.contains(edge.dependent())
                    && !edge.prerequisite().equals(edge.dependent())) {
                connected.add(edge.prerequisite());
                connected.add(edge.dependent());
            }
        }
        List<CatalogProblem> warnings = new ArrayList<>();
        Set<TopicKey> reported = new HashSet<>();
        for (DesiredTopic topic : desired.topics()) {
            if (!connected.contains(topic.key()) && reported.add(topic.key())) {
                warnings.add(new CatalogProblem(topic.key().subjectCode() + "/topics.csv",
                        topic.line(), ("topic %s has no prerequisite and nothing depends on it. If"
                                + " that is right, leave it; if not, add its edges to"
                                + " prerequisites.csv").formatted(topic.key().topicCode())));
            }
        }
        return sorted(warnings);
    }

    /** Duplicate codes and duplicate positions, both within one subject. */
    private static void checkTopics(DesiredCatalogue desired, Map<TopicKey, DesiredTopic> known,
            List<CatalogProblem> problems) {

        Map<String, Map<Integer, DesiredTopic>> positions = new HashMap<>();
        for (DesiredTopic topic : desired.topics()) {
            String file = topic.key().subjectCode() + "/topics.csv";
            DesiredTopic clash = known.putIfAbsent(topic.key(), topic);
            if (clash != null) {
                problems.add(new CatalogProblem(file, topic.line(),
                        ("duplicate topic code %s, already declared on line %d. Each code must be"
                                + " unique within the subject: rename one of the two")
                                .formatted(topic.key().topicCode(), clash.line())));
                continue;
            }
            DesiredTopic sharing = positions
                    .computeIfAbsent(topic.key().subjectCode(), subject -> new HashMap<>())
                    .putIfAbsent(topic.position(), topic);
            if (sharing != null) {
                problems.add(new CatalogProblem(file, topic.line(),
                        ("position %d is already taken by %s on line %d. Give each topic its own"
                                + " position")
                                .formatted(topic.position(), sharing.key().topicCode(), sharing.line())));
            }
        }
    }

    /**
     * Holes in a subject's positions.
     *
     * <p>Positions are the curricular order, and they run 1, 2, 3 … with nothing skipped. A hole
     * is almost always a deleted row whose neighbours were not renumbered, and it is invisible
     * in a spreadsheet sorted by position. Repetition is {@link #checkTopics}'s to report; this
     * looks only at the distinct positions, so one mistake is not reported twice.
     */
    private static void checkPositions(DesiredCatalogue desired, Map<TopicKey, DesiredTopic> known,
            List<CatalogProblem> problems) {

        Map<String, TreeMap<Integer, DesiredTopic>> bySubject = new LinkedHashMap<>();
        for (DesiredTopic topic : known.values()) {
            bySubject.computeIfAbsent(topic.key().subjectCode(), subject -> new TreeMap<>())
                    .putIfAbsent(topic.position(), topic);
        }
        bySubject.forEach((subject, positions) -> {
            int expected = 1;
            DesiredTopic previous = null;
            for (Map.Entry<Integer, DesiredTopic> entry : positions.entrySet()) {
                if (entry.getKey() != expected) {
                    DesiredTopic topic = entry.getValue();
                    String after = previous == null
                            ? "the first position is %d".formatted(entry.getKey())
                            : "position %d (%s) is followed by %d".formatted(previous.position(),
                                    previous.key().topicCode(), entry.getKey());
                    problems.add(new CatalogProblem(subject + "/topics.csv", topic.line(),
                            ("positions of %s have a hole: %s. Number the topics 1, 2, 3 … in the"
                                    + " order the subject is taught, without skipping")
                                    .formatted(subject, after)));
                }
                expected = entry.getKey() + 1;
                previous = entry.getValue();
            }
        });
    }

    /** Unknown references, self-edges, and the same pair asserted twice. */
    private static void checkEdges(DesiredCatalogue desired, Map<TopicKey, DesiredTopic> known,
            List<CatalogProblem> problems) {

        Map<DesiredEdge.Pair, DesiredEdge> seen = new HashMap<>();
        for (DesiredEdge edge : desired.edges()) {
            boolean unresolved = false;
            for (TopicKey end : List.of(edge.prerequisite(), edge.dependent())) {
                if (!known.containsKey(end)) {
                    problems.add(new CatalogProblem(edge.file(), edge.line(), unknown(desired, end)));
                    unresolved = true;
                }
            }
            if (unresolved) {
                continue;
            }
            if (edge.prerequisite().equals(edge.dependent())) {
                problems.add(new CatalogProblem(edge.file(), edge.line(),
                        "a topic cannot be its own prerequisite: " + edge.prerequisite()
                                + ". Remove the row"));
                continue;
            }
            DesiredEdge duplicate = seen.putIfAbsent(edge.pair(), edge);
            if (duplicate != null) {
                problems.add(new CatalogProblem(edge.file(), edge.line(),
                        "duplicate edge %s -> %s, already declared in %s line %d. Keep one".formatted(
                                edge.prerequisite(), edge.dependent(), duplicate.file(),
                                duplicate.line())));
            }
        }
    }

    /**
     * Why a reference resolves to nothing, and what to do about it.
     *
     * <p>Two different mistakes look identical in a row. A topic code that does not exist is a
     * typo to fix in place. A subject that was not read is either a subject with no directory or
     * a run restricted with {@code --subject} to one side of a cross-subject edge — and the fix
     * there is to run over both subjects, not to touch the row.
     */
    private static String unknown(DesiredCatalogue desired, TopicKey end) {
        if (desired.subjects().containsKey(end.subjectCode())) {
            return ("unknown topic %s: there is no code %s in %s/topics.csv. Check the spelling,"
                    + " or add the topic").formatted(end, end.topicCode(), end.subjectCode());
        }
        return ("unknown topic %s (subject %s was not read): an edge across subjects needs both"
                + " subjects in the same run. Add the directory %s/, or run without --subject")
                .formatted(end, end.subjectCode(), end.subjectCode());
    }

    /**
     * What {@code CURATED} requires: a cited source, and a recorded review of the subject.
     *
     * <p>{@code CURATED} is the label a result may be attributed to as "curated graph", so it
     * has to be earned. A source alone is not a review — the example catalogue cites chapters of
     * a real textbook and nobody who teaches the subject has read it — and a review with no
     * source leaves nothing to check the edge against. The review that counts is the one of the
     * subject whose file declares the edge, because that is the file the reviewer signed.
     */
    private static void checkProvenance(DesiredCatalogue desired, List<CatalogProblem> problems) {
        for (DesiredEdge edge : desired.edges()) {
            if (edge.provenance() != EdgeProvenance.CURATED) {
                continue;
            }
            if (!edge.citesASource()) {
                problems.add(new CatalogProblem(edge.file(), edge.line(),
                        ("edge %s -> %s is CURATED and cites no source. Fill source_reference with"
                                + " where the dependency is stated, or write PROPOSED")
                                .formatted(edge.prerequisite(), edge.dependent())));
            }
            if (desired.reviewsOf(edge.declaredIn()).isEmpty()) {
                problems.add(new CatalogProblem(edge.file(), edge.line(),
                        ("edge %s -> %s is CURATED but %s has no recorded review. CURATED means"
                                + " reviewed by someone who teaches the subject: record the review"
                                + " in %s/reviews.csv, or write PROPOSED")
                                .formatted(edge.prerequisite(), edge.dependent(), edge.declaredIn(),
                                        edge.declaredIn())));
            }
        }
    }

    /**
     * Cycles, reported as the whole path.
     *
     * <p>Only edges whose ends both exist are walked: an unknown reference has already been
     * reported and following it would produce a second, more confusing message about the same
     * line. The graph is every subject read, so a cycle that closes only by crossing subjects is
     * found like any other.
     *
     * <p>Both strengths are walked. A {@code SOFT} edge is a preference rather than a
     * constraint, but a preference cycle is still a specification the optimiser cannot satisfy
     * in the order it asks for, and the database trigger does not distinguish them either.
     */
    private static void checkCycles(DesiredCatalogue desired, Map<TopicKey, DesiredTopic> known,
            List<CatalogProblem> problems) {

        Map<TopicKey, List<TopicKey>> graph = new LinkedHashMap<>();
        Map<DesiredEdge.Pair, DesiredEdge> byPair = new LinkedHashMap<>();
        for (DesiredEdge edge : desired.edges()) {
            if (!known.containsKey(edge.prerequisite()) || !known.containsKey(edge.dependent())
                    || edge.prerequisite().equals(edge.dependent())) {
                continue;
            }
            graph.computeIfAbsent(edge.prerequisite(), key -> new ArrayList<>()).add(edge.dependent());
            byPair.putIfAbsent(edge.pair(), edge);
        }

        for (List<TopicKey> cycle : PrerequisiteCycles.in(graph)) {
            TopicKey last = cycle.get(cycle.size() - 2);
            TopicKey closes = cycle.getLast();
            DesiredEdge closing = byPair.get(new DesiredEdge.Pair(last, closes));
            problems.add(new CatalogProblem(closing.file(), closing.line(),
                    "cycle detected:" + PrerequisiteCycles.draw(cycle, key -> nameOf(known, key))
                            + "\n      Cut one edge of the loop: the one on this line closes it"));
        }
    }

    private static String nameOf(Map<TopicKey, DesiredTopic> known, TopicKey key) {
        DesiredTopic topic = known.get(key);
        return topic == null ? null : topic.name();
    }

    private static List<CatalogProblem> sorted(List<CatalogProblem> problems) {
        problems.sort(Comparator.comparing(CatalogProblem::file)
                .thenComparingInt(CatalogProblem::line));
        return List.copyOf(problems);
    }
}
