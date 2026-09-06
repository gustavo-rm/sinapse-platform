package br.com.sinapse.platform.curriculum.internal.persistence;

import br.com.sinapse.platform.curriculum.internal.domain.Topic;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Topics. */
public interface TopicRepository extends JpaRepository<Topic, UUID> {

    /**
     * The topics of one subject, in curricular order.
     *
     * @param subjectId subject
     * @return its topics, ordered by position
     */
    List<Topic> findBySubjectIdOrderByPositionAsc(UUID subjectId);

    /**
     * The topics of several subjects, in one query.
     *
     * <p>Rule R7: the snapshot assembly reads several subjects at once, and a per-subject
     * lookup would give it one query per subject.
     *
     * @param subjectIds subjects
     * @return their topics, ordered by subject and then by position
     */
    List<Topic> findBySubjectIdInOrderBySubjectIdAscPositionAsc(Collection<UUID> subjectIds);

    /**
     * Topics by identifier, in one query.
     *
     * @param topicIds topics
     * @return the ones that exist, in curricular order
     */
    List<Topic> findByIdInOrderBySubjectIdAscPositionAsc(Collection<UUID> topicIds);

    /**
     * A topic by its natural key.
     *
     * @param subjectId subject
     * @param code      natural key within the subject
     * @return the topic, if the pair is known
     */
    Optional<Topic> findBySubjectIdAndCode(UUID subjectId, String code);
}
