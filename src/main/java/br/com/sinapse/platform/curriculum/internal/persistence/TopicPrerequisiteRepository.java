package br.com.sinapse.platform.curriculum.internal.persistence;

import br.com.sinapse.platform.curriculum.internal.domain.TopicPrerequisite;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Prerequisite edges. */
public interface TopicPrerequisiteRepository extends JpaRepository<TopicPrerequisite, UUID> {

    /**
     * The edge between an ordered pair, if there is one.
     *
     * @param prerequisiteTopicId topic that would come first
     * @param dependentTopicId    topic that would depend on it
     * @return the edge
     */
    Optional<TopicPrerequisite> findByPrerequisiteTopicIdAndDependentTopicId(
            UUID prerequisiteTopicId, UUID dependentTopicId);

    /**
     * Every edge with at least one endpoint among the given topics.
     *
     * <p>"At least one" rather than "both": an edge reaching into this set from outside it is
     * a constraint on the set, and dropping it because its other end is elsewhere would
     * discard exactly the cross-subject edges the graph exists for.
     *
     * @param topicIds topics of interest
     * @return the edges touching them
     */
    @Query("""
            select edge from TopicPrerequisite edge
            where edge.prerequisiteTopicId in :topicIds
               or edge.dependentTopicId in :topicIds
            """)
    List<TopicPrerequisite> findTouching(@Param("topicIds") Collection<UUID> topicIds);

    /**
     * Every edge with both endpoints among the given topics.
     *
     * <p>The induced subgraph, which is what an ordering of those topics is computed from: an
     * edge leading out of the set says nothing about the order of what is inside it.
     *
     * @param topicIds topics of interest
     * @return the edges between them
     */
    @Query("""
            select edge from TopicPrerequisite edge
            where edge.prerequisiteTopicId in :topicIds
              and edge.dependentTopicId in :topicIds
            """)
    List<TopicPrerequisite> findWithin(@Param("topicIds") Collection<UUID> topicIds);

    /**
     * The edges that point at a topic: its direct prerequisites.
     *
     * @param dependentTopicId topic to look at
     * @return the edges into it
     */
    List<TopicPrerequisite> findByDependentTopicId(UUID dependentTopicId);
}
