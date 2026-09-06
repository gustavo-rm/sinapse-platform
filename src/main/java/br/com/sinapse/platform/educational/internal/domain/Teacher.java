package br.com.sinapse.platform.educational.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * The professional link of an account that owns classrooms.
 *
 * <p>Separate from the {@code TEACHER} role, which lives in identity and authorises the
 * login. This entity holds what the role cannot: a display name a student will see on an
 * invite preview, and the institution as free text.
 *
 * <p>There is no {@code Institution} entity, and adding one is explicitly out of scope. The
 * name is text because nothing in the pilot needs it to be anything else, and an entity would
 * be the first step towards multi-tenancy, which section 4.3 rules out as speculative.
 *
 * <p>{@code account_id} is a foreign key to {@code account}, which is allowed: educational is
 * already permitted to depend on identity, so the key points the way the dependency already
 * goes (rule R4).
 */
@Entity
@Table(name = "teacher")
public class Teacher {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    /**
     * Name shown to a student deciding whether to accept an invite. Personal data of the
     * teacher, and the reason an invite preview does not need to read identity at all.
     */
    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "institution_name")
    private String institutionName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** What an erased teacher's display name becomes. The column is {@code not null}. */
    private static final String ANONYMIZED_NAME = "anonymized";

    /** For JPA. */
    protected Teacher() {
    }

    /**
     * Records the professional link of an account.
     *
     * @param id              identifier
     * @param accountId       account that holds the teacher role
     * @param displayName     name a student will see
     * @param institutionName institution as free text, or {@code null}
     * @param createdAt       instant the link was recorded
     */
    public Teacher(UUID id, UUID accountId, String displayName, String institutionName,
            Instant createdAt) {
        this.id = id;
        this.accountId = accountId;
        this.displayName = displayName;
        this.institutionName = institutionName;
        this.createdAt = createdAt;
    }

    /** Identifier of the teacher record. */
    public UUID id() {
        return id;
    }

    /** Account that holds the teacher role. */
    public UUID accountId() {
        return accountId;
    }

    /** Name a student sees. Personal data: never log it. */
    public String displayName() {
        return displayName;
    }

    /** Institution as free text. */
    public String institutionName() {
        return institutionName;
    }

    /** Instant the link was recorded. */
    public Instant createdAt() {
        return createdAt;
    }

    /**
     * Corrects the name and the institution.
     *
     * @param newDisplayName     name a student will see
     * @param newInstitutionName institution, or {@code null}
     */
    public void reviseTo(String newDisplayName, String newInstitutionName) {
        this.displayName = newDisplayName;
        this.institutionName = newInstitutionName;
    }

    /**
     * Empties the teacher of the personal data it holds, keeping the row.
     *
     * <p>The row cannot go: classrooms hang off it and ended enrollments hang off those, and
     * those enrollments are the audit trail of the access a teacher had to a student's data.
     * Erasing the teacher would erase other people's record of who could see them, which is
     * the opposite of what an Article 18 request is for.
     *
     * <p>What can go is the name, which is the holder's own personal data and is the only thing
     * here that identifies anybody. The column is {@code not null}, so it is overwritten with a
     * value that names nobody rather than nulled.
     *
     * <p>ADR 0011's table does not mention this row. That is a gap in the ADR rather than a
     * decision it took: a teacher can be a data subject too, and leaving their name behind
     * would be a leak the whole erasure exists to prevent.
     */
    public void anonymize() {
        this.displayName = ANONYMIZED_NAME;
        this.institutionName = null;
    }
}
