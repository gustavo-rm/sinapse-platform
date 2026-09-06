package br.com.sinapse.platform.identity.internal.domain;

import br.com.sinapse.platform.identity.api.ConsentPurpose;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * The text of a consent term, as published for one purpose at one point in time.
 *
 * <p>Without it we could prove <em>that</em> someone consented and not <em>to what</em>
 * (section 5.1). A published version is therefore never edited: a new wording is a new
 * row, and the consent records that reference the old one keep pointing at the text their
 * holder actually read.
 *
 * <p>The composite unique key {@code (id, purpose)} exists so that a consent record can
 * reference the terms and its purpose together, which is what makes it impossible to
 * record consent to the research terms under the essential purpose.
 */
@Entity
@Table(name = "terms_version")
public class TermsVersion {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, updatable = false)
    private ConsentPurpose purpose;

    @Column(name = "version", nullable = false, updatable = false)
    private String version;

    @Column(name = "body", nullable = false, updatable = false)
    private String body;

    @Column(name = "published_at", nullable = false, updatable = false)
    private Instant publishedAt;

    /** For JPA. */
    protected TermsVersion() {
    }

    /**
     * Publishes a wording.
     *
     * @param id          identifier of this version
     * @param purpose     purpose the wording covers
     * @param version     label the operator identifies this wording by
     * @param body        full text presented to the holder
     * @param publishedAt instant from which this wording is the one in force
     */
    public TermsVersion(UUID id, ConsentPurpose purpose, String version, String body, Instant publishedAt) {
        this.id = id;
        this.purpose = purpose;
        this.version = version;
        this.body = body;
        this.publishedAt = publishedAt;
    }

    /** Identifier of this version. */
    public UUID id() {
        return id;
    }

    /** Purpose this wording covers. */
    public ConsentPurpose purpose() {
        return purpose;
    }

    /** Label of this wording. */
    public String version() {
        return version;
    }

    /** Full text presented to the holder. */
    public String body() {
        return body;
    }

    /** Instant from which this wording is in force. */
    public Instant publishedAt() {
        return publishedAt;
    }
}
