package br.com.sinapse.platform.curriculum.internal.domain;

import br.com.sinapse.platform.curriculum.api.EffortTier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A topic: the unit planning works in.
 *
 * <p>An aggregate root, referenced by identifier from three other contexts.
 *
 * <p>The subject is referenced by identifier rather than by an association even though both
 * live in this module. The reason is the same one that made the topic a root: a topic is read
 * far more often than a subject is, and an association would put a join on every one of those
 * reads to fetch something the caller did not ask for.
 */
@Entity
@Table(name = "topic")
public class Topic {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "subject_id", nullable = false, updatable = false)
    private UUID subjectId;

    /**
     * Stable natural key, unique within the subject.
     *
     * <p>It exists because the curated catalogue lives in version-controlled CSV. A human
     * cannot author against UUIDs, and authoring against position would silently repoint
     * every edge the moment anyone reordered a chapter. It is never derived from the name:
     * a name is display text and gets corrected.
     */
    @Column(name = "code", nullable = false, updatable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    /**
     * Curricular ordering inside the subject. The source for seeding textbook-order edges,
     * and unique within the subject under a deferred constraint so that a whole subject can
     * be reordered in one transaction.
     */
    @Column(name = "position", nullable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(name = "effort_tier", nullable = false)
    private EffortTier effortTier;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** For JPA. */
    protected Topic() {
    }

    /**
     * Registers a topic.
     *
     * @param id         identifier
     * @param subjectId  subject it belongs to
     * @param code       stable natural key within the subject
     * @param name       display name
     * @param position   curricular ordering
     * @param effortTier ordinal effort band
     * @param createdAt  instant it entered the catalogue
     */
    public Topic(UUID id, UUID subjectId, String code, String name, int position, EffortTier effortTier,
            Instant createdAt) {
        this.id = id;
        this.subjectId = subjectId;
        this.code = code;
        this.name = name;
        this.position = position;
        this.effortTier = effortTier;
        this.createdAt = createdAt;
    }

    /** Identifier other contexts reference. */
    public UUID id() {
        return id;
    }

    /** Subject this topic belongs to. Every topic belongs to exactly one. */
    public UUID subjectId() {
        return subjectId;
    }

    /** Stable natural key within the subject. */
    public String code() {
        return code;
    }

    /** Display name. */
    public String name() {
        return name;
    }

    /** Curricular ordering inside the subject. */
    public int position() {
        return position;
    }

    /** Ordinal effort band. Never minutes. */
    public EffortTier effortTier() {
        return effortTier;
    }

    /** Instant it entered the catalogue. */
    public Instant createdAt() {
        return createdAt;
    }

    /**
     * Applies a curated revision of what this topic is.
     *
     * @param newName       display name
     * @param newPosition   curricular ordering
     * @param newEffortTier effort band
     */
    public void reviseTo(String newName, int newPosition, EffortTier newEffortTier) {
        this.name = newName;
        this.position = newPosition;
        this.effortTier = newEffortTier;
    }

    /**
     * Moves the topic in the curricular order.
     *
     * @param newPosition new position within the subject
     */
    public void moveTo(int newPosition) {
        this.position = newPosition;
    }
}
