package br.com.sinapse.platform.identity.support;

import br.com.sinapse.platform.identity.api.AccountStatus;
import br.com.sinapse.platform.identity.api.ConsentGrantedBy;
import br.com.sinapse.platform.identity.api.ConsentPurpose;
import br.com.sinapse.platform.identity.internal.domain.Account;
import br.com.sinapse.platform.identity.internal.domain.AccountTokenPurpose;
import br.com.sinapse.platform.identity.internal.persistence.AccountRepository;
import br.com.sinapse.platform.identity.internal.service.EmailVerificationService;
import br.com.sinapse.platform.identity.internal.service.RegistrationService;
import br.com.sinapse.platform.identity.internal.service.TermsService;
import br.com.sinapse.platform.identity.internal.domain.ConsentEvidence;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The states an identity test starts from.
 *
 * <p>Two ways of building one, and the difference matters.
 *
 * <p>Whatever the production code can build, the production code builds: an adult account is
 * registered and verified through the real services, so a test never asserts against a state
 * the application could not have reached.
 *
 * <p>What it cannot build is written straight into the database. Two cases need that. A
 * holder below the consent age, because registration refuses one on purpose and the guardian
 * flow that would otherwise create it belongs to the next version. And a consent granted
 * years ago, because {@code granted_at} is append-only and no clock in a test can be moved
 * backwards past a trigger. Writing the row is not a way around the rules — it is how the
 * rows the rules are about get to exist, and the rules are then exercised against them.
 */
public class IdentityFixtures {

    /** Zone used unless a test cares about the zone. */
    public static final ZoneId DEFAULT_ZONE = ZoneId.of("America/Sao_Paulo");

    /** Password used unless a test cares about the password. */
    public static final String DEFAULT_PASSWORD = "correct-horse-battery-staple";

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    private RegistrationService registration;

    @Autowired
    private EmailVerificationService verification;

    @Autowired
    private TermsService terms;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private CapturingAccountNotifier notifications;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Clock clock;

    /** An address no other test is using. */
    public String uniqueEmail() {
        return "holder-" + SEQUENCE.incrementAndGet() + "-" + UUID.randomUUID() + "@example.com";
    }

    /** A date of birth that makes the holder comfortably of age. */
    public LocalDate adultDateOfBirth() {
        return LocalDate.ofInstant(clock.instant(), DEFAULT_ZONE).minusYears(30);
    }

    /**
     * Registers an adult, who ends up awaiting verification of the address.
     *
     * @param email address to register
     * @return the account
     */
    public Account registerAdult(String email) {
        return registerAdult(email, adultDateOfBirth(), false);
    }

    /**
     * Registers an adult.
     *
     * @param email           address to register
     * @param dateOfBirth     date of birth to declare
     * @param acceptsResearch whether the optional research purpose is accepted too
     * @return the account
     */
    public Account registerAdult(String email, LocalDate dateOfBirth, boolean acceptsResearch) {
        Map<ConsentPurpose, UUID> accepted = new LinkedHashMap<>();
        for (ConsentPurpose purpose : ConsentPurpose.essentialPurposes()) {
            accepted.put(purpose, currentTermsId(purpose));
        }
        if (acceptsResearch) {
            accepted.put(ConsentPurpose.ACADEMIC_RESEARCH, currentTermsId(ConsentPurpose.ACADEMIC_RESEARCH));
        }
        return registration.register(new RegistrationService.RegistrationCommand(
                email, DEFAULT_PASSWORD, dateOfBirth, DEFAULT_ZONE, accepted, evidence()));
    }

    /**
     * Registers an adult and verifies the address, which is the whole path to an active
     * account.
     *
     * @param email address to register
     * @return the account, reloaded after activation
     */
    public Account activeAdult(String email) {
        return activeAdult(email, false);
    }

    /**
     * Registers an adult and verifies the address.
     *
     * @param email           address to register
     * @param acceptsResearch whether the optional research purpose is accepted too
     * @return the account, reloaded after activation
     */
    public Account activeAdult(String email, boolean acceptsResearch) {
        Account account = registerAdult(email, adultDateOfBirth(), acceptsResearch);
        verification.verify(notifications.requireToken(account.id(), AccountTokenPurpose.EMAIL_VERIFICATION));
        return reload(account.id());
    }

    /** The wording currently in force for a purpose. */
    public UUID currentTermsId(ConsentPurpose purpose) {
        return terms.currentFor(purpose)
                .orElseThrow(() -> new AssertionError("No terms published for " + purpose))
                .id();
    }

    /** Reads an account back from the database. */
    public Account reload(UUID accountId) {
        return accounts.findById(accountId)
                .orElseThrow(() -> new AssertionError("No account " + accountId));
    }

    /** Evidence for an act performed by a test. */
    public ConsentEvidence evidence() {
        return ConsentEvidence.ofApiForm("203.0.113.7", "integration-test");
    }

    /**
     * Writes an account straight into the database, in whatever state the test needs.
     *
     * @param email       address
     * @param dateOfBirth date of birth
     * @param status      state to start in
     * @return the identifier of the new account
     */
    public UUID insertAccount(String email, LocalDate dateOfBirth, AccountStatus status) {
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update("""
                insert into account (id, email, password_hash, date_of_birth, time_zone, status,
                                     created_at, activated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id, email, "not-a-real-hash", java.sql.Date.valueOf(dateOfBirth), DEFAULT_ZONE.getId(),
                status.name(), java.sql.Timestamp.from(now),
                status == AccountStatus.ACTIVE ? java.sql.Timestamp.from(now) : null);
        jdbc.update("insert into account_role (account_id, role) values (?, 'STUDENT')", id);
        return id;
    }

    /**
     * Writes a guardian for an account.
     *
     * @param accountId account the guardian answers for
     * @param verified  whether the verification is to be recorded as done
     * @return the identifier of the guardian record
     */
    public UUID insertGuardian(UUID accountId, boolean verified) {
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update("""
                insert into guardian (id, account_id, full_name, email, relationship, verified_at,
                                      created_at)
                values (?, ?, ?, ?, ?, ?, ?)
                """,
                id, accountId, "Guardian of record", "guardian-" + id + "@example.com", "MOTHER",
                verified ? java.sql.Timestamp.from(now) : null, java.sql.Timestamp.from(now));
        return id;
    }

    /**
     * Writes a consent record with a chosen instant of granting.
     *
     * <p>The only way to obtain a consent that was granted in the past: the column is
     * append-only and the trigger refuses to move it.
     *
     * @param accountId  holder
     * @param purpose    purpose
     * @param grantedBy  grantor
     * @param guardianId guardian, when the grantor is one
     * @param grantedAt  instant of the act
     * @return the identifier of the record
     */
    public UUID insertConsent(UUID accountId, ConsentPurpose purpose, ConsentGrantedBy grantedBy,
            UUID guardianId, Instant grantedAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into consent_record (id, account_id, purpose, terms_version_id, granted_by,
                                            guardian_id, granted_at, evidence)
                values (?, ?, ?, ?, ?, ?, ?, cast(? as jsonb))
                """,
                id, accountId, purpose.name(), currentTermsId(purpose), grantedBy.name(), guardianId,
                java.sql.Timestamp.from(grantedAt),
                "{\"collectionMethod\":\"API_FORM\",\"ageVerificationMethod\":\"SELF_DECLARED_DATE_OF_BIRTH\"}");
        return id;
    }

    /**
     * An account that was activated while its holder was below the consent age, with the
     * guardian consent that made that lawful.
     *
     * <p>Nothing in this version can produce this state — registration refuses a minor — and
     * everything about the majority transition is about accounts in it. So it is written
     * directly, and then read by the code under test exactly as a real row would be.
     *
     * @param email        address
     * @param dateOfBirth  date of birth, chosen so the holder reaches the threshold when the
     *                     test wants them to
     * @param consentedAt  instant the guardian consented, which has to be before the holder
     *                     reached the threshold
     * @return the identifier of the account
     */
    public UUID insertAccountConsentedByGuardian(String email, LocalDate dateOfBirth, Instant consentedAt) {
        UUID accountId = insertAccount(email, dateOfBirth, AccountStatus.ACTIVE);
        UUID guardianId = insertGuardian(accountId, true);
        for (ConsentPurpose purpose : ConsentPurpose.essentialPurposes()) {
            insertConsent(accountId, purpose, ConsentGrantedBy.GUARDIAN, guardianId, consentedAt);
        }
        return accountId;
    }
}
