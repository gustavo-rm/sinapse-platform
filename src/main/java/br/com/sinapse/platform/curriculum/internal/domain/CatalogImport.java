package br.com.sinapse.platform.curriculum.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/**
 * One applied import of the curated catalogue.
 *
 * <p><strong>Read here, written elsewhere.</strong> The importer of ADR 0014 is not part of
 * this version; what this module needs today is to answer which revision is in effect, so that
 * a generation job can record it. Marked {@link Immutable} because a record of what was
 * applied and when is not something to edit, and mapped over only the columns that question
 * needs — the counters are the importer's to fill in and its to map.
 */
@Entity
@Immutable
@Table(name = "catalog_import")
public class CatalogImport {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Git commit of the files that produced this state. */
    @Column(name = "source_revision", nullable = false, updatable = false)
    private String sourceRevision;

    @Column(name = "applied_at", nullable = false, updatable = false)
    private Instant appliedAt;

    /** Subjects the import touched. */
    @Column(name = "subjects_affected", nullable = false, updatable = false)
    private int subjectsAffected;

    /** Topics created. */
    @Column(name = "topics_added", nullable = false, updatable = false)
    private int topicsAdded;

    /** Topics whose name, position or effort band changed. */
    @Column(name = "topics_updated", nullable = false, updatable = false)
    private int topicsUpdated;

    /** Edges created. */
    @Column(name = "edges_added", nullable = false, updatable = false)
    private int edgesAdded;

    /** Edges corrected. */
    @Column(name = "edges_updated", nullable = false, updatable = false)
    private int edgesUpdated;

    /** Edges removed. */
    @Column(name = "edges_removed", nullable = false, updatable = false)
    private int edgesRemoved;

    /** Anything a curator should be able to read back. */
    @Column(name = "notes", updatable = false)
    private String notes;

    /** For JPA. */
    protected CatalogImport() {
    }

    /**
     * Records one applied state of the catalogue.
     *
     * <p>Written once and never again, which is what {@code @Immutable} says and what the row
     * is for: it is the evidence that a given plan was generated against a given curation, and
     * evidence that can be edited afterwards is not evidence.
     *
     * @param id             identifier
     * @param sourceRevision git revision of the files that produced this state
     * @param appliedAt      when it was applied
     * @param counts         what the import changed
     * @param notes          anything a curator should read back, or {@code null}
     */
    public CatalogImport(UUID id, String sourceRevision, Instant appliedAt, Counts counts,
            String notes) {
        this.id = id;
        this.sourceRevision = sourceRevision;
        this.appliedAt = appliedAt;
        this.subjectsAffected = counts.subjects();
        this.topicsAdded = counts.topicsAdded();
        this.topicsUpdated = counts.topicsUpdated();
        this.edgesAdded = counts.edgesAdded();
        this.edgesUpdated = counts.edgesUpdated();
        this.edgesRemoved = counts.edgesRemoved();
        this.notes = notes;
    }

    /**
     * What one import changed.
     *
     * @param subjects      subjects touched
     * @param topicsAdded   topics created
     * @param topicsUpdated topics revised
     * @param edgesAdded    edges created
     * @param edgesUpdated  edges corrected
     * @param edgesRemoved  edges removed
     */
    public record Counts(int subjects, int topicsAdded, int topicsUpdated, int edgesAdded,
            int edgesUpdated, int edgesRemoved) {
    }

    /** Identifier of the import. */
    public UUID id() {
        return id;
    }

    /** Git commit of the files that produced this state. */
    public String sourceRevision() {
        return sourceRevision;
    }

    /** When it was applied. */
    public Instant appliedAt() {
        return appliedAt;
    }
}
