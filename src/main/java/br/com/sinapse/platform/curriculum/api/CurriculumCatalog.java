package br.com.sinapse.platform.curriculum.api;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads of the curated catalogue.
 *
 * <p>Every lookup that could be asked about many things at once takes a set. Architecture
 * rule R7: a module that offers only unit lookups forces whatever composes above it into one
 * query per row, and the composition layers — the snapshot assembly, the read models — are
 * exactly the callers here.
 */
public interface CurriculumCatalog {

    /**
     * Every subject in the catalogue, by code.
     *
     * <p>Returned whole. The catalogue is a curated global list of the order of dozens, not
     * something that grows with usage, so there is nothing to page through.
     *
     * @return the subjects
     */
    List<SubjectView> subjects();

    /**
     * A subject by its stable code.
     *
     * @param code natural key
     * @return the subject, if the code is known
     */
    Optional<SubjectView> subjectByCode(String code);

    /**
     * The topics of a set of subjects, in curricular order within each subject.
     *
     * @param subjectIds subjects to read
     * @return the topics, ordered by subject and then by position
     */
    List<TopicView> topicsOfSubjects(Collection<UUID> subjectIds);

    /**
     * Topics by identifier.
     *
     * @param topicIds topics to read
     * @return the ones that exist, in curricular order
     */
    List<TopicView> topicsByIds(Collection<UUID> topicIds);

    /**
     * Subjects by identifier.
     *
     * <p>Keyed rather than listed because every caller of this method is putting a subject
     * name next to something that already holds a {@code subjectId} — a planned session, a
     * topic, a classroom — and a list would only be turned into this map by the caller.
     *
     * @param subjectIds subjects to read
     * @return the ones that exist, keyed by identifier
     */
    Map<UUID, SubjectView> subjectsByIds(Collection<UUID> subjectIds);
}
