package br.com.sinapse.platform.curriculum.internal.service;

import br.com.sinapse.platform.curriculum.api.PrerequisiteCycleException;
import br.com.sinapse.platform.curriculum.api.TopicView;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.UUID;

/**
 * Orders a set of topics so that no prerequisite among them is violated.
 *
 * <p>Kahn's algorithm, on the subgraph induced by the topics given. Nothing here touches the
 * database, which is what lets the rule be tested against a hand-built graph rather than
 * against whatever a fixture happened to insert.
 *
 * <p><strong>The order is deterministic.</strong> At each step several topics may be equally
 * free to come next, and any of them would be a valid answer. Choosing arbitrarily would make
 * the result depend on hash iteration order, and a plan has to be reproducible from its
 * snapshot (ADR 0007) — the same input has to give the same plan, or a result cannot be
 * attributed to anything. So ties are broken by curricular position, then by topic code, then
 * by identifier, which is a total order and therefore always decides.
 */
public final class TopologicalOrder {

    /**
     * Curricular position first, because it is the curator's own judgement about sequence and
     * the closest thing to a preference the graph has. Code and identifier only exist to make
     * the comparator total.
     */
    private static final Comparator<TopicView> TIE_BREAK = Comparator
            .comparingInt(TopicView::position)
            .thenComparing(TopicView::code)
            .thenComparing(TopicView::id);

    private TopologicalOrder() {
    }

    /**
     * Produces the order.
     *
     * @param topics topics to order
     * @param edges  prerequisite pairs; any pair with an endpoint outside {@code topics} is
     *               ignored, because it says nothing about the order of what is inside
     * @return the topics, in an order consistent with every edge among them
     * @throws PrerequisiteCycleException if no such order exists
     */
    public static List<TopicView> of(Collection<TopicView> topics, Collection<Edge> edges) {
        Map<UUID, TopicView> byId = new HashMap<>();
        topics.forEach(topic -> byId.put(topic.id(), topic));

        Map<UUID, List<UUID>> dependents = new HashMap<>();
        Map<UUID, Integer> remainingPrerequisites = new HashMap<>();
        byId.keySet().forEach(id -> remainingPrerequisites.put(id, 0));

        for (Edge edge : edges) {
            if (!byId.containsKey(edge.prerequisiteTopicId()) || !byId.containsKey(edge.dependentTopicId())) {
                continue;
            }
            dependents.computeIfAbsent(edge.prerequisiteTopicId(), ignored -> new ArrayList<>())
                    .add(edge.dependentTopicId());
            remainingPrerequisites.merge(edge.dependentTopicId(), 1, Integer::sum);
        }

        PriorityQueue<TopicView> available = new PriorityQueue<>(TIE_BREAK);
        remainingPrerequisites.forEach((id, remaining) -> {
            if (remaining == 0) {
                available.add(byId.get(id));
            }
        });

        List<TopicView> ordered = new ArrayList<>(byId.size());
        while (!available.isEmpty()) {
            TopicView next = available.poll();
            ordered.add(next);
            for (UUID dependent : dependents.getOrDefault(next.id(), List.of())) {
                if (remainingPrerequisites.merge(dependent, -1, Integer::sum) == 0) {
                    available.add(byId.get(dependent));
                }
            }
        }

        if (ordered.size() != byId.size()) {
            // Whatever is left still has a prerequisite that never became available, which
            // can only happen if those topics depend on each other in a loop.
            throw new PrerequisiteCycleException();
        }
        return List.copyOf(ordered);
    }

    /**
     * A directed pair, which is all the algorithm needs to know about an edge.
     *
     * <p>Strength is deliberately absent. ADR 0006 notes that a cyclic {@code SOFT} edge
     * would not break the algorithm, being only a penalty, and that v1 nevertheless validates
     * the union as acyclic — keeping two regimes of validation is complexity that only pays
     * off if the case turns up. The ordering follows the same rule as the database.
     *
     * @param prerequisiteTopicId topic that comes first
     * @param dependentTopicId    topic that depends on it
     */
    public record Edge(UUID prerequisiteTopicId, UUID dependentTopicId) {
    }
}
