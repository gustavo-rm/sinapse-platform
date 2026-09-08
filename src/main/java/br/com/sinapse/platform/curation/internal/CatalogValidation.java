package br.com.sinapse.platform.curation.internal;

import br.com.sinapse.platform.curation.internal.model.CatalogProblem;
import br.com.sinapse.platform.curation.internal.model.DesiredCatalogue;
import br.com.sinapse.platform.curation.internal.model.DesiredEdge;
import br.com.sinapse.platform.curation.internal.model.DesiredTopic;
import br.com.sinapse.platform.curation.internal.model.TopicKey;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        checkEdges(desired, known, problems);
        checkCycles(desired, known, problems);

        problems.sort(java.util.Comparator.comparing(CatalogProblem::file)
                .thenComparingInt(CatalogProblem::line));
        return List.copyOf(problems);
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
                        "duplicate topic code %s, already declared on line %d"
                                .formatted(topic.key().topicCode(), clash.line())));
                continue;
            }
            DesiredTopic sharing = positions
                    .computeIfAbsent(topic.key().subjectCode(), subject -> new HashMap<>())
                    .putIfAbsent(topic.position(), topic);
            if (sharing != null) {
                problems.add(new CatalogProblem(file, topic.line(),
                        "position %d is already taken by %s on line %d"
                                .formatted(topic.position(), sharing.key().topicCode(), sharing.line())));
            }
        }
    }

    /** Unknown references, self-edges, and the same pair asserted twice. */
    private static void checkEdges(DesiredCatalogue desired, Map<TopicKey, DesiredTopic> known,
            List<CatalogProblem> problems) {

        Map<DesiredEdge.Pair, DesiredEdge> seen = new HashMap<>();
        for (DesiredEdge edge : desired.edges()) {
            String file = edge.prerequisite().subjectCode() + "/prerequisites.csv";
            boolean unresolved = false;
            for (TopicKey end : List.of(edge.prerequisite(), edge.dependent())) {
                if (!known.containsKey(end)) {
                    problems.add(new CatalogProblem(file, edge.line(),
                            "unknown topic " + end + (desired.subjects().containsKey(end.subjectCode())
                                    ? ""
                                    : " (subject " + end.subjectCode() + " was not read)")));
                    unresolved = true;
                }
            }
            if (unresolved) {
                continue;
            }
            if (edge.prerequisite().equals(edge.dependent())) {
                problems.add(new CatalogProblem(file, edge.line(),
                        "a topic cannot be its own prerequisite: " + edge.prerequisite()));
                continue;
            }
            DesiredEdge duplicate = seen.putIfAbsent(edge.pair(), edge);
            if (duplicate != null) {
                problems.add(new CatalogProblem(file, edge.line(),
                        "duplicate edge %s -> %s, already declared on line %d".formatted(
                                edge.prerequisite(), edge.dependent(), duplicate.line())));
            }
        }
    }

    /**
     * Cycles, reported as the whole path.
     *
     * <p>Depth-first with an explicit stack of the path being explored, so that when an edge
     * closes back onto something already on it, the cycle is the tail of that stack. Only edges
     * whose ends both exist are walked: an unknown reference has already been reported and
     * following it would produce a second, more confusing message about the same line.
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

        Set<TopicKey> settled = new HashSet<>();
        Set<TopicKey> reported = new HashSet<>();
        for (TopicKey start : graph.keySet()) {
            if (settled.contains(start)) {
                continue;
            }
            walk(start, graph, settled, new LinkedHashSet<>(), byPair, known, reported, problems);
        }
    }

    /**
     * One depth-first walk, carrying the path so a cycle can be named in full.
     *
     * <p>Iterative rather than recursive: a curated subject is hundreds of topics deep in the
     * worst case, and a validator that overflows the stack on a large file fails exactly when it
     * is most needed.
     */
    private static void walk(TopicKey start, Map<TopicKey, List<TopicKey>> graph,
            Set<TopicKey> settled, Set<TopicKey> path, Map<DesiredEdge.Pair, DesiredEdge> byPair,
            Map<TopicKey, DesiredTopic> known, Set<TopicKey> reported,
            List<CatalogProblem> problems) {

        Deque<Step> stack = new ArrayDeque<>();
        stack.push(new Step(start, graph.getOrDefault(start, List.of()).iterator()));
        path.add(start);

        while (!stack.isEmpty()) {
            Step step = stack.peek();
            if (!step.next.hasNext()) {
                stack.pop();
                path.remove(step.node);
                settled.add(step.node);
                continue;
            }
            TopicKey next = step.next.next();
            if (path.contains(next)) {
                reportCycle(next, path, byPair, known, reported, problems);
                continue;
            }
            if (settled.contains(next)) {
                continue;
            }
            path.add(next);
            stack.push(new Step(next, graph.getOrDefault(next, List.of()).iterator()));
        }
    }

    /**
     * Prints the cycle as the path a curator has to cut.
     *
     * <p>Reported once per cycle rather than once per edge on it. A three-edge cycle reported
     * three times, from three different lines, reads like three problems.
     */
    private static void reportCycle(TopicKey closes, Set<TopicKey> path,
            Map<DesiredEdge.Pair, DesiredEdge> byPair, Map<TopicKey, DesiredTopic> known,
            Set<TopicKey> reported, List<CatalogProblem> problems) {

        List<TopicKey> ordered = new ArrayList<>(path);
        List<TopicKey> cycle = new ArrayList<>(ordered.subList(ordered.indexOf(closes), ordered.size()));
        if (!reported.add(cycle.stream().min(java.util.Comparator.comparing(TopicKey::toString))
                .orElse(closes))) {
            return;
        }

        StringBuilder drawn = new StringBuilder("cycle detected:");
        List<TopicKey> closed = new ArrayList<>(cycle);
        closed.add(closes);
        for (int index = 0; index < closed.size(); index++) {
            TopicKey node = closed.get(index);
            drawn.append("\n      ").append(node);
            DesiredTopic topic = known.get(node);
            if (topic != null) {
                drawn.append("  (").append(topic.name()).append(')');
            }
            if (index < closed.size() - 1) {
                drawn.append(" ->");
            }
        }

        DesiredEdge closing = byPair.get(new DesiredEdge.Pair(cycle.getLast(), closes));
        String file = closes.subjectCode() + "/prerequisites.csv";
        int line = 0;
        if (closing != null) {
            file = closing.prerequisite().subjectCode() + "/prerequisites.csv";
            line = closing.line();
        }
        problems.add(new CatalogProblem(file, line, drawn.toString()));
    }

    /** One frame of the walk: where we are, and which of its edges are left. */
    private record Step(TopicKey node, java.util.Iterator<TopicKey> next) {
    }
}
