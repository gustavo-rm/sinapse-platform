package br.com.sinapse.platform.curriculum.internal.domain;

import br.com.sinapse.platform.curriculum.api.EdgeProvenance;
import br.com.sinapse.platform.curriculum.api.EdgeStrength;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A directed prerequisite edge: {@code prerequisiteTopicId} is studied before
 * {@code dependentTopicId}.
 *
 * <p>An aggregate root, because the graph is read as a set rather than from one topic
 * outwards. Both endpoints are identifiers and not associations, for the same reason.
 *
 * <p><strong>Not append-only, and that is the difference from a consent record.</strong> An
 * edge is a curated assertion about the world, and assertions about the world get corrected.
 * A consent record is a legal fact about something that happened, which does not. So this
 * entity has setters, the table has no immutability trigger, and {@code createdBy} plus
 * {@code createdAt} carry the audit trail instead.
 *
 * <p>{@code createdBy} holds the account of the curator as a bare identifier with no foreign
 * key. A foreign key would point from the base layer towards identity, which rule R4 forbids;
 * and this module deliberately does not know what an account is.
 *
 * <p>Acyclicity is not checked here. It is enforced by a trigger with a recursive query and
 * an advisory lock, because two concurrent inserts under read committed do not see each other
 * and each would pass a check made in application code while together closing a cycle. See
 * ADR 0006.
 */
@Entity
@Table(name = "topic_prerequisite")
public class TopicPrerequisite {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "prerequisite_topic_id", nullable = false, updatable = false)
    private UUID prerequisiteTopicId;

    @Column(name = "dependent_topic_id", nullable = false, updatable = false)
    private UUID dependentTopicId;

    @Enumerated(EnumType.STRING)
    @Column(name = "strength", nullable = false)
    private EdgeStrength strength;

    @Enumerated(EnumType.STRING)
    @Column(name = "provenance", nullable = false)
    private EdgeProvenance provenance;

    @Column(name = "source_reference")
    private String sourceReference;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** For JPA. */
    protected TopicPrerequisite() {
    }

    /**
     * Asserts an edge.
     *
     * @param id                  identifier
     * @param prerequisiteTopicId topic that comes first
     * @param dependentTopicId    topic that depends on it
     * @param strength            constraint or penalty
     * @param provenance          where the assertion came from
     * @param sourceReference     citation, or {@code null}
     * @param createdBy           account asserting it, or {@code null}
     * @param createdAt           instant of the assertion
     */
    public TopicPrerequisite(UUID id, UUID prerequisiteTopicId, UUID dependentTopicId,
            EdgeStrength strength, EdgeProvenance provenance, String sourceReference, UUID createdBy,
            Instant createdAt) {
        this.id = id;
        this.prerequisiteTopicId = prerequisiteTopicId;
        this.dependentTopicId = dependentTopicId;
        this.strength = strength;
        this.provenance = provenance;
        this.sourceReference = sourceReference;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    /** Identifier of the edge. */
    public UUID id() {
        return id;
    }

    /** Topic that comes first. */
    public UUID prerequisiteTopicId() {
        return prerequisiteTopicId;
    }

    /** Topic that depends on it. */
    public UUID dependentTopicId() {
        return dependentTopicId;
    }

    /** Constraint or penalty. */
    public EdgeStrength strength() {
        return strength;
    }

    /** Where the assertion came from. */
    public EdgeProvenance provenance() {
        return provenance;
    }

    /** Citation of the source, when there is one. */
    public String sourceReference() {
        return sourceReference;
    }

    /** Account that asserted the edge, when it is known. */
    public UUID createdBy() {
        return createdBy;
    }

    /** Instant of the assertion. */
    public Instant createdAt() {
        return createdAt;
    }

    /**
     * Whether a human asserted this edge.
     *
     * <p>Read by the textbook-order seeding, which must never overwrite a curated judgement
     * with a mechanical one.
     */
    public boolean isCurated() {
        return provenance == EdgeProvenance.CURATED;
    }

    /**
     * Corrects the edge.
     *
     * <p>The endpoints are not correctable, and the mapping says so. An edge between two
     * different topics is a different assertion, not a correction of this one, and letting it
     * be edited in place would lose the fact that the original was ever made.
     *
     * @param newStrength        new strength
     * @param newProvenance      new provenance
     * @param newSourceReference new citation, or {@code null}
     */
    public void correctTo(EdgeStrength newStrength, EdgeProvenance newProvenance,
            String newSourceReference) {
        this.strength = newStrength;
        this.provenance = newProvenance;
        this.sourceReference = newSourceReference;
    }
}
