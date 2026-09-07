package br.com.sinapse.platform.curriculum.api;

import java.util.List;
import java.util.UUID;

/**
 * The write side of the catalogue.
 *
 * <p>Published rather than kept internal because of who calls it. Curation is not done
 * through a screen and never through the API: authoring happens in a spreadsheet, the source
 * of truth is the exported CSV in git, and a command-line importer applies it (ADR 0014).
 * That importer goes through this interface rather than through the tables, so that the rules
 * below hold whoever is writing.
 *
 * <p>The argument is not convenience. The curated graph and the effort tiers are inputs to
 * the ablation experiment, and curation done straight against the database produces no
 * history and makes it impossible to attribute a result to a state of the catalogue.
 *
 * <p><strong>Edges are not append-only.</strong> Unlike a consent record or an enrollment,
 * they are curated data and being able to correct them is the point; {@code createdBy} and
 * {@code createdAt} keep the audit trail.
 *
 * <p><strong>Topics are a different matter.</strong> {@link #removeTopic} exists because a
 * declarative import has to be able to express a topic that is gone, but it is never the
 * default: the importer reports a topic missing from the file and leaves it alone unless it is
 * told otherwise. When it is told otherwise, the foreign keys from downstream modules are what
 * refuse — see {@link TopicStillReferencedException}.
 */
public interface CatalogCuration {

    /**
     * Registers a subject, or returns the one that already has this code.
     *
     * <p>Idempotent by code, because an importer applies the same file repeatedly and a
     * declarative import has to be able to run twice with no effect the second time.
     *
     * @param code stable natural key
     * @param name display name, updated when it differs
     * @return the subject
     */
    SubjectView defineSubject(String code, String name);

    /**
     * Registers a topic, or updates the one that already has this code in this subject.
     *
     * <p>Idempotent by {@code (subjectId, code)}, for the same reason. The code is never
     * derived from the name: it is the identity a curator refers to a topic by, and deriving
     * it would repoint edges the moment someone fixed a typo.
     *
     * @param definition what the topic is
     * @return the topic
     */
    TopicView defineTopic(TopicDefinition definition);

    /**
     * Rewrites the curricular order of a whole subject, in one transaction.
     *
     * <p>It has to be one transaction, and the unique constraint on position has to be
     * deferred, or the intermediate states of any reorder that is not a rotation would
     * violate it. Swapping two topics has no order of two updates that is valid at every
     * step.
     *
     * @param subjectId subject to reorder
     * @param order     every topic of the subject and its new position
     */
    void reorderTopics(UUID subjectId, List<TopicPosition> order);

    /**
     * Records a prerequisite edge.
     *
     * @param definition the edge
     * @return the stored edge
     * @throws PrerequisiteCycleException if it would close a cycle
     */
    PrerequisiteEdgeView addEdge(EdgeDefinition definition);

    /**
     * Corrects an existing edge.
     *
     * <p>Changing the strength of an edge is revalidated for cycles as well. It looks like it
     * could not matter — the endpoints are not moving — but the trigger fires on update too,
     * and relying on "this particular change is harmless" is how a validation gets bypassed
     * by the next change that is not.
     *
     * @param edgeId          edge to correct
     * @param strength        new strength
     * @param provenance      new provenance
     * @param sourceReference new citation, or {@code null}
     * @return the corrected edge
     * @throws PrerequisiteCycleException if the correction would close a cycle
     */
    PrerequisiteEdgeView correctEdge(UUID edgeId, EdgeStrength strength, EdgeProvenance provenance,
            String sourceReference);

    /**
     * Removes an edge.
     *
     * @param edgeId edge to remove
     */
    void removeEdge(UUID edgeId);

    /**
     * Seeds edges from the curricular order of a subject.
     *
     * <p>A table of contents is a valid topological order, even if not a minimal one, so the
     * cheapest way to start a graph is to assert it and let curation handle the exceptions.
     * The edges are {@link EdgeStrength#SOFT} because the ordering of a book is a claim about
     * a sensible sequence and not about a dependency, and their provenance says where they
     * came from so that the ablation can tell them apart from curated ones.
     *
     * <p>Idempotent, and it never overwrites an edge that a human asserted: a pair that
     * already has an edge is left exactly as it is, whatever its provenance.
     *
     * @param subjectId subject whose order to seed from
     * @param curatedBy account running the seeding, or {@code null}
     * @return what it did
     */
    SeedingResult seedTextbookOrder(UUID subjectId, UUID curatedBy);

    /**
     * What a topic is.
     *
     * @param subjectId  subject it belongs to
     * @param code       stable natural key within the subject
     * @param name       display name
     * @param position   curricular ordering
     * @param effortTier ordinal effort band
     */
    record TopicDefinition(UUID subjectId, String code, String name, int position, EffortTier effortTier) {
    }

    /**
     * One entry of a reorder.
     *
     * @param topicId  topic to move
     * @param position its new position
     */
    record TopicPosition(UUID topicId, int position) {
    }

    /**
     * What an edge is.
     *
     * @param prerequisiteTopicId topic that comes first
     * @param dependentTopicId    topic that depends on it
     * @param strength            constraint or penalty
     * @param provenance          where it came from
     * @param sourceReference     citation, or {@code null}
     * @param createdBy           account asserting it, or {@code null}
     */
    record EdgeDefinition(
            UUID prerequisiteTopicId,
            UUID dependentTopicId,
            EdgeStrength strength,
            EdgeProvenance provenance,
            String sourceReference,
            UUID createdBy) {
    }

    /**
     * The outcome of a seeding run.
     *
     * @param created edges written
     * @param kept    consecutive pairs that already had an edge and were left untouched
     */
    record SeedingResult(int created, int kept) {
    }

    /**
     * Removes a topic.
     *
     * <p>Only ever reached through an explicit instruction: a topic simply absent from an
     * imported file is reported and kept, because study sessions may reference it and evidence
     * does not disappear as a side effect of curation (ADR 0014).
     *
     * <p>Its edges go with it. They are curated data whose whole meaning was the topic at
     * either end, so leaving them would be leaving edges to nothing.
     *
     * @param topicId topic to remove
     * @throws TopicStillReferencedException if anything outside this module still points at it
     */
    void removeTopic(UUID topicId);

    /**
     * Records that a state of the catalogue was applied.
     *
     * <p>This is the row that makes ADR 0014's provenance argument real: the curated graph and
     * the effort tiers are inputs to the ablation experiment, and without knowing which
     * revision of the CSV files was in force, a result cannot be attributed to a state of the
     * catalogue. A generation job points at the import that was current when it ran.
     *
     * @param record what was applied, and from which revision of the files
     * @return identifier of the recorded import
     */
    UUID recordImport(ImportRecord record);

    /**
     * One applied import.
     *
     * @param sourceRevision  git revision of the CSV files this state came from. Carries a
     *                        {@code -dirty} suffix when the working tree had uncommitted
     *                        changes, because a revision that does not describe what was
     *                        applied is worse than an obviously untrustworthy one
     * @param subjectsAffected subjects the import touched
     * @param topicsAdded     topics created
     * @param topicsUpdated   topics whose name, position or effort band changed
     * @param edgesAdded      edges created
     * @param edgesUpdated    edges corrected
     * @param edgesRemoved    edges removed
     * @param notes           anything a curator should be able to read back, or {@code null}
     */
    record ImportRecord(
            String sourceRevision,
            int subjectsAffected,
            int topicsAdded,
            int topicsUpdated,
            int edgesAdded,
            int edgesUpdated,
            int edgesRemoved,
            String notes) {
    }
}
