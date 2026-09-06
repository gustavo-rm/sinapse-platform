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

    /** For JPA. */
    protected CatalogImport() {
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
