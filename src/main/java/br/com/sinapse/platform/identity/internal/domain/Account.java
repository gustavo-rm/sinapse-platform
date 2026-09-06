package br.com.sinapse.platform.identity.internal.domain;

import br.com.sinapse.platform.identity.api.AccountRole;
import br.com.sinapse.platform.identity.api.AccountStatus;
import br.com.sinapse.platform.identity.internal.error.AccountTransitionException;
import br.com.sinapse.platform.identity.internal.error.DateOfBirthImmutableException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Root of the account aggregate: credentials, date of birth, time zone, status, roles and
 * the guardian.
 *
 * <p>The guardian is an internal entity rather than an aggregate of its own because it
 * only exists in relation to one account and is never reached on its own (section 5.1).
 *
 * <p>What this class does <em>not</em> hold is consent. A consent record grows without
 * bound and is queried independently, so it is a root of its own, and the invariant that
 * binds the two — an active account has a valid essential consent — crosses both. Section
 * 5.3 places that invariant in a single service that writes both, which is why the
 * transitions below are visible to the aggregate but the decision to call them is not
 * taken here.
 */
@Entity
@Table(name = "account")
public class Account {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Case-insensitive, as declared by the migration. The type is spelled out so that
     * Hibernate's schema validation compares {@code citext} against {@code citext} rather
     * than against the {@code varchar} it would otherwise expect for a string.
     */
    @Column(name = "email", nullable = false, columnDefinition = "citext")
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Convert(converter = ZoneIdConverter.class)
    @Column(name = "time_zone", nullable = false)
    private ZoneId timeZone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AccountStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "suspended_at")
    private Instant suspendedAt;

    @Column(name = "anonymized_at")
    private Instant anonymizedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "account_role", joinColumns = @JoinColumn(name = "account_id"))
    @Column(name = "role")
    @Enumerated(EnumType.STRING)
    private Set<AccountRole> roles = new LinkedHashSet<>();

    @OneToOne(mappedBy = "account", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Guardian guardian;

    /**
     * What an erased account's e-mail becomes.
     *
     * <p>The column is {@code not null} and {@code citext}, so something has to go in it. This
     * carries no personal data — the identifier it embeds is already the primary key of the
     * row — and stays unique, so it would still be safe if the partial index that excludes
     * anonymised accounts were ever widened.
     */
    private static final String ANONYMIZED_EMAIL_PREFIX = "anonymized:";

    /** A password hash that is not one, so that nothing can ever verify against it. */
    private static final String ANONYMIZED_PASSWORD_HASH = "anonymized";

    /** The epoch: a date that is obviously not a date of birth. */
    private static final LocalDate ANONYMIZED_DATE_OF_BIRTH = LocalDate.EPOCH;

    /** UTC, because the column is {@code not null} and a shell keeps no local times. */
    private static final ZoneId ANONYMIZED_TIME_ZONE = ZoneId.of("UTC");

    /** For JPA. */
    protected Account() {
    }

    private Account(UUID id, String email, String passwordHash, LocalDate dateOfBirth, ZoneId timeZone,
            Instant createdAt) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.dateOfBirth = dateOfBirth;
        this.timeZone = timeZone;
        this.status = AccountStatus.PENDING_VERIFICATION;
        this.createdAt = createdAt;
        this.roles.add(AccountRole.STUDENT);
    }

    /**
     * Opens an account that has not proved control of its e-mail address yet.
     *
     * <p>{@link AccountRole#STUDENT} is the only role granted. How an account comes to
     * hold {@link AccountRole#TEACHER} is an unanswered product question (P2), and no code
     * path may guess an answer to it.
     *
     * @param id           identifier of the new account
     * @param email        address given at registration
     * @param passwordHash Argon2id hash of the chosen password; the password itself never
     *                     enters the aggregate
     * @param dateOfBirth  self-declared date of birth
     * @param timeZone     IANA zone the holder's local times are interpreted in
     * @param createdAt    instant of registration
     * @return the account, in {@link AccountStatus#PENDING_VERIFICATION}
     */
    public static Account register(UUID id, String email, String passwordHash, LocalDate dateOfBirth,
            ZoneId timeZone, Instant createdAt) {
        return new Account(id, email, passwordHash, dateOfBirth, timeZone, createdAt);
    }

    /** Identifier of this account. */
    public UUID id() {
        return id;
    }

    /** Address the account is reached at. Personal data: never log it. */
    public String email() {
        return email;
    }

    /** Argon2id hash of the current password. */
    public String passwordHash() {
        return passwordHash;
    }

    /** Self-declared date of birth. Personal data: never log it. */
    public LocalDate dateOfBirth() {
        return dateOfBirth;
    }

    /** Zone every local time of this holder is interpreted in. */
    public ZoneId timeZone() {
        return timeZone;
    }

    /** Current state of the account. */
    public AccountStatus status() {
        return status;
    }

    /** Instant of registration. */
    public Instant createdAt() {
        return createdAt;
    }

    /** Instant the account first became active, or {@code null} while it never did. */
    public Instant activatedAt() {
        return activatedAt;
    }

    /** Instant of the current suspension, or {@code null}. */
    public Instant suspendedAt() {
        return suspendedAt;
    }

    /** Instant of anonymisation, or {@code null}. */
    public Instant anonymizedAt() {
        return anonymizedAt;
    }

    /** Roles held, never empty. */
    public Set<AccountRole> roles() {
        return Collections.unmodifiableSet(roles);
    }

    /** The guardian, present only for a holder below the configured threshold. */
    public Guardian guardian() {
        return guardian;
    }

    /** Whether a guardian has been registered and verified for this account. */
    public boolean hasVerifiedGuardian() {
        return guardian != null && guardian.isVerified();
    }

    /**
     * The date the holder reaches the configured consent age threshold.
     *
     * @param ageThreshold configured threshold, in years
     * @return the date from which consent has to be the holder's own
     */
    public LocalDate majorityDate(int ageThreshold) {
        return dateOfBirth.plusYears(ageThreshold);
    }

    /**
     * Age of the holder on a given date.
     *
     * @param date date to measure at
     * @return completed years
     */
    public int ageOn(LocalDate date) {
        return Period.between(dateOfBirth, date).getYears();
    }

    /**
     * Age of the holder at a given instant, read in the holder's own zone.
     *
     * <p>The zone matters: a consent granted late in the evening of a birthday is granted
     * by a person who is already of age where they live, and by a minor in UTC. ADR 0009
     * settles which of the two the application believes.
     *
     * @param instant instant to measure at
     * @return completed years
     */
    public int ageAt(Instant instant) {
        return ageOn(LocalDate.ofInstant(instant, timeZone));
    }

    /**
     * Corrects the date of birth.
     *
     * <p>Invariant 2. Before activation this is a correction of a typo; after it, it is
     * the way a minor walks through the gate, so it is refused. Section 5.3 leaves an
     * audited administrative operation open for the case that has to be corrected after
     * the fact; that operation does not exist yet and is not this method.
     *
     * @param corrected the corrected date
     * @throws DateOfBirthImmutableException if the account has ever been active
     */
    public void correctDateOfBirth(LocalDate corrected) {
        if (activatedAt != null) {
            throw new DateOfBirthImmutableException();
        }
        this.dateOfBirth = corrected;
    }

    /** Replaces the password hash. Callers revoke the sessions; the aggregate does not. */
    public void changePasswordHash(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
    }

    /**
     * Registers the guardian of a holder below the threshold.
     *
     * @param newGuardian guardian of this account
     */
    public void assignGuardian(Guardian newGuardian) {
        newGuardian.attachTo(this);
        this.guardian = newGuardian;
    }

    /**
     * Moves the account to {@link AccountStatus#ACTIVE}.
     *
     * <p>Whether the account <em>may</em> become active is not decided here: it depends on
     * consent records, which belong to another aggregate, and section 5.3 puts that
     * decision in the single service that writes both.
     *
     * @param now instant of the transition
     * @throws AccountTransitionException if the account is anonymised
     */
    public void activate(Instant now) {
        if (status == AccountStatus.ANONYMIZED) {
            throw new AccountTransitionException();
        }
        this.status = AccountStatus.ACTIVE;
        this.suspendedAt = null;
        if (this.activatedAt == null) {
            this.activatedAt = now;
        }
    }

    /**
     * Parks the account until a guardian consents.
     *
     * @throws AccountTransitionException if the account is anonymised
     */
    public void awaitGuardianConsent() {
        if (status == AccountStatus.ANONYMIZED) {
            throw new AccountTransitionException();
        }
        this.status = AccountStatus.PENDING_GUARDIAN_CONSENT;
    }

    /**
     * Suspends the account.
     *
     * @param now instant of the suspension
     * @throws AccountTransitionException if the account is anonymised
     */
    public void suspend(Instant now) {
        if (status == AccountStatus.ANONYMIZED) {
            throw new AccountTransitionException();
        }
        this.status = AccountStatus.SUSPENDED;
        this.suspendedAt = now;
    }

    /**
     * Moves the account to its terminal state and empties it.
     *
     * <p>What is left is the shell that anchors the two records which survive an Article 18
     * erasure: the consent records that prove the legal basis for treatment that already
     * happened, and the ended enrollments that account for the access a teacher once had.
     * Neither can point at nothing.
     *
     * <p><strong>The columns are overwritten, not nulled.</strong> ADR 0011 says they are
     * nulled and the schema declares all four {@code not null}; dropping that to serve the
     * last thing that ever happens to an account would weaken every live row in the table.
     * What goes in instead identifies nobody: an opaque value derived from the identifier that
     * is already the primary key, a password hash that cannot verify, the epoch, and UTC.
     *
     * <p>The e-mail is free for a new registration immediately, because the unique index on it
     * is partial over accounts that have not been anonymised.
     *
     * <p>Idempotent, so that an erasure which failed and rolled back can be retried whole.
     *
     * @param now instant of the anonymisation
     */
    public void anonymize(Instant now) {
        if (status == AccountStatus.ANONYMIZED) {
            return;
        }
        this.status = AccountStatus.ANONYMIZED;
        this.anonymizedAt = now;
        this.email = ANONYMIZED_EMAIL_PREFIX + id;
        this.passwordHash = ANONYMIZED_PASSWORD_HASH;
        this.dateOfBirth = ANONYMIZED_DATE_OF_BIRTH;
        this.timeZone = ANONYMIZED_TIME_ZONE;
        // orphanRemoval on the association: nulling it deletes the guardian row, which is a
        // third party's data with no basis for retention once the holder's is gone.
        this.guardian = null;
    }
}
