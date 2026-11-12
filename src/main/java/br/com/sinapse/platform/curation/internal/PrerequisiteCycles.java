package br.com.sinapse.platform.curation.internal;

import br.com.sinapse.platform.curation.internal.model.TopicKey;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Finds the cycles of a prerequisite graph, each as the path a curator has to cut.
 *
 * <p>Separate from {@link CatalogValidation} because two graphs are walked: the one the files
 * describe, and — before an import restricted to some subjects — the files combined with the
 * edges already in the catalogue that the run does not read. A cycle that closes only through
 * the second kind is the one that would otherwise reach the database trigger.
 */
final class PrerequisiteCycles {

    private PrerequisiteCycles() {
    }

    /**
     * Every cycle, once each.
     *
     * <p>Depth-first with an explicit stack of the path being explored, so that when an edge
     * closes back onto something already on it, the cycle is the tail of that stack. Iterative
     * rather than recursive: a curated subject is hundreds of topics deep in the worst case, and
     * a validator that overflows the stack on a large file fails exactly when it is most needed.
     *
     * <p>Reported once per cycle rather than once per edge on it. A three-edge cycle reported
     * three times, from three different lines, reads like three problems.
     *
     * @param graph prerequisite to its dependents
     * @return each cycle as a closed path: the first topic is repeated at the end
     */
    static List<List<TopicKey>> in(Map<TopicKey, List<TopicKey>> graph) {
        List<List<TopicKey>> cycles = new ArrayList<>();
        Set<TopicKey> settled = new HashSet<>();
        Set<TopicKey> reported = new HashSet<>();
        for (TopicKey start : graph.keySet()) {
            if (!settled.contains(start)) {
                walk(start, graph, settled, reported, cycles);
            }
        }
        return cycles;
    }

    private static void walk(TopicKey start, Map<TopicKey, List<TopicKey>> graph,
            Set<TopicKey> settled, Set<TopicKey> reported, List<List<TopicKey>> cycles) {

        Set<TopicKey> path = new LinkedHashSet<>();
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
                List<TopicKey> ordered = new ArrayList<>(path);
                List<TopicKey> cycle = new ArrayList<>(
                        ordered.subList(ordered.indexOf(next), ordered.size()));
                TopicKey identity = cycle.stream().min(Comparator.comparing(TopicKey::toString))
                        .orElse(next);
                if (reported.add(identity)) {
                    cycle.add(next);
                    cycles.add(List.copyOf(cycle));
                }
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
     * Draws a closed path one topic per line, with the name when there is one.
     *
     * @param cycle closed path, as {@link #in} returns it
     * @param names topic name, or {@code null} when unknown
     * @return the drawing, starting with a line break
     */
    static String draw(List<TopicKey> cycle, Function<TopicKey, String> names) {
        StringBuilder drawn = new StringBuilder();
        for (int index = 0; index < cycle.size(); index++) {
            TopicKey node = cycle.get(index);
            drawn.append("\n      ").append(node);
            String name = names.apply(node);
            if (name != null) {
                drawn.append("  (").append(name).append(')');
            }
            if (index < cycle.size() - 1) {
                drawn.append(" ->");
            }
        }
        return drawn.toString();
    }

    /** One frame of the walk: where we are, and which of its edges are left. */
    private record Step(TopicKey node, Iterator<TopicKey> next) {
    }
}
