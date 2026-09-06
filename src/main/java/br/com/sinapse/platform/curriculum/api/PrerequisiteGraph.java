package br.com.sinapse.platform.curriculum.api;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * The prerequisite graph, read as a graph.
 *
 * <p>These three queries are what the orchestration layer assembles the core snapshot from.
 * They are phrased as questions about a <em>set</em> of subjects or topics, because that is
 * how a snapshot is built: one plan covers several subjects, and asking per topic would
 * issue one query per row.
 *
 * <p>The graph is single and global. Edges cross subjects, because real prerequisites do —
 * trigonometry precedes kinematics — and partitioning per subject would look simpler while
 * forbidding exactly the most informative edges.
 */
public interface PrerequisiteGraph {

    /**
     * Every edge with at least one endpoint in the given subjects.
     *
     * <p>"At least one" and not "both" on purpose. An edge from a topic of another subject
     * into one of these is a constraint on planning these, and dropping it because its other
     * end is elsewhere would silently discard the cross-subject edges the graph exists for.
     *
     * @param subjectIds subjects of interest
     * @return the edges, with their strength and provenance
     */
    List<PrerequisiteEdgeView> edgesTouchingSubjects(Collection<UUID> subjectIds);

    /**
     * The direct prerequisites of a topic: the topics that must be studied before it.
     *
     * @param topicId topic to look at
     * @return its immediate predecessors, in curricular order
     */
    List<TopicView> directPrerequisitesOf(UUID topicId);

    /**
     * An order in which a set of topics can be studied without violating a prerequisite
     * between two of them.
     *
     * <p>Only edges with both endpoints inside the set are considered. An edge leading out of
     * the set says nothing about the order of what is inside it, and one leading in is a
     * constraint on when the set may be started rather than on how it is ordered.
     *
     * <p>The order is deterministic: among topics that are equally free to come next, the one
     * that comes first in its subject's curricular order wins, and the topic code breaks any
     * remaining tie. Two calls with the same input produce the same list, which matters
     * because a plan has to be reproducible from its snapshot (ADR 0007).
     *
     * @param topicIds topics to order
     * @return the topics, in an order consistent with every edge among them
     * @throws PrerequisiteCycleException if no such order exists, which means a cycle reached
     *                                    the graph despite the database refusing to store one
     */
    List<TopicView> topologicalOrder(Collection<UUID> topicIds);
}
