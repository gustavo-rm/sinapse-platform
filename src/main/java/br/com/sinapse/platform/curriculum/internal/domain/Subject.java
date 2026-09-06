package br.com.sinapse.platform.curriculum.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A subject of the curated catalogue.
 *
 * <p>Holds a code and a name and nothing else. In particular it does not hold its topics: a
 * topic is a root of its own, and an association here would invite loading a hundred and
 * fifty rows to answer a question about one of them.
 */
@Entity
@Table(name = "subject")
public class Subject {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Stable natural key, unique across the catalogue. The curator refers to a subject by
     * this, in a spreadsheet, and it is what an edge in a CSV file is written against.
     */
    @Column(name = "code", nullable = false, updatable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** For JPA. */
    protected Subject() {
    }

    /**
     * Registers a subject.
     *
     * @param id        identifier
     * @param code      stable natural key
     * @param name      display name
     * @param createdAt instant it entered the catalogue
     */
    public Subject(UUID id, String code, String name, Instant createdAt) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.createdAt = createdAt;
    }

    /** Identifier other contexts reference. */
    public UUID id() {
        return id;
    }

    /** Stable natural key. */
    public String code() {
        return code;
    }

    /** Display name. */
    public String name() {
        return name;
    }

    /** Instant it entered the catalogue. */
    public Instant createdAt() {
        return createdAt;
    }

    /**
     * Renames the subject.
     *
     * <p>The name is editable and the code is not, which is the whole point of having both:
     * fixing a typo in a display name must not repoint anything that refers to the subject.
     *
     * @param newName new display name
     */
    public void rename(String newName) {
        this.name = newName;
    }
}
