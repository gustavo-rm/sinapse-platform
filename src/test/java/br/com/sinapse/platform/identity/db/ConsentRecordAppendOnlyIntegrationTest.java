package br.com.sinapse.platform.identity.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.sinapse.platform.identity.api.AccountStatus;
import br.com.sinapse.platform.identity.api.ConsentGrantedBy;
import br.com.sinapse.platform.identity.api.ConsentPurpose;
import br.com.sinapse.platform.identity.support.IdentityIntegrationTest;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessException;

/**
 * The guarantees that belong to the database, checked where they live.
 *
 * <p>Everything here goes through plain SQL rather than through the repositories. That is the
 * point: an assertion made through the ORM proves that the mapping is currently right, and
 * these constraints exist precisely because a mapping can stop being right. They are the
 * defence against our own defect, not against an attacker, and the only way to test a
 * defence against a defect is to write the defective statement by hand.
 */
class ConsentRecordAppendOnlyIntegrationTest extends IdentityIntegrationTest {

    @Autowired
    private Clock clock;

    @Test
    void thePartialIndexRefusesASecondValidConsentForThePurpose() {
        UUID accountId = adultAccount();
        insertConsent(accountId, ConsentPurpose.LEARNING_DATA_PROCESSING);

        assertThatThrownBy(() -> insertConsent(accountId, ConsentPurpose.LEARNING_DATA_PROCESSING))
                .as("ux_active_consent turns \"at most one consent in force per purpose\" into a "
                        + "guarantee of the database rather than a discipline of the code")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void thePartialIndexAllowsANewConsentOnceTheFirstIsWithdrawn() {
        UUID accountId = adultAccount();
        UUID first = insertConsent(accountId, ConsentPurpose.LEARNING_DATA_PROCESSING);

        jdbc.update("update consent_record set revoked_at = ? where id = ?",
                java.sql.Timestamp.from(clock.instant()), first);
        UUID second = insertConsent(accountId, ConsentPurpose.LEARNING_DATA_PROCESSING);

        assertThat(second).isNotEqualTo(first);
        assertThat(jdbc.queryForObject(
                "select count(*) from consent_record where account_id = ?", Integer.class, accountId))
                .as("reaffirmation writes a new record and preserves the previous one")
                .isEqualTo(2);
    }

    @Test
    void theTriggerRefusesAnUpdateToAnyColumnButRevokedAt() {
        UUID accountId = adultAccount();
        UUID recordId = insertConsent(accountId, ConsentPurpose.LEARNING_DATA_PROCESSING);

        assertThatThrownBy(() -> jdbc.update(
                "update consent_record set granted_at = ? where id = ?",
                java.sql.Timestamp.from(clock.instant().minusSeconds(86400)), recordId))
                .as("granted_at is the instant the act happened; moving it rewrites the record")
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");

        assertThatThrownBy(() -> jdbc.update(
                "update consent_record set granted_by = 'GUARDIAN' where id = ?", recordId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");

        assertThatThrownBy(() -> jdbc.update(
                "update consent_record set purpose = 'ACADEMIC_RESEARCH' where id = ?", recordId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");

        assertThatThrownBy(() -> jdbc.update(
                "update consent_record set evidence = cast('{}' as jsonb) where id = ?", recordId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }

    @Test
    void theTriggerAllowsTheSingleWriteOfRevokedAt() {
        UUID accountId = adultAccount();
        UUID recordId = insertConsent(accountId, ConsentPurpose.LEARNING_DATA_PROCESSING);
        Instant revokedAt = clock.instant();

        int updated = jdbc.update("update consent_record set revoked_at = ? where id = ?",
                java.sql.Timestamp.from(revokedAt), recordId);

        assertThat(updated).isEqualTo(1);
    }

    @Test
    void theTriggerRefusesASecondWriteOfRevokedAt() {
        UUID accountId = adultAccount();
        UUID recordId = insertConsent(accountId, ConsentPurpose.LEARNING_DATA_PROCESSING);
        jdbc.update("update consent_record set revoked_at = ? where id = ?",
                java.sql.Timestamp.from(clock.instant()), recordId);

        assertThatThrownBy(() -> jdbc.update("update consent_record set revoked_at = ? where id = ?",
                java.sql.Timestamp.from(clock.instant().plusSeconds(60)), recordId))
                .as("one write, and only one: a withdrawal that can be moved is a withdrawal "
                        + "nobody can prove the date of")
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("cannot be changed once set");
    }

    @Test
    void theTriggerRefusesADelete() {
        UUID accountId = adultAccount();
        UUID recordId = insertConsent(accountId, ConsentPurpose.LEARNING_DATA_PROCESSING);

        assertThatThrownBy(() -> jdbc.update("delete from consent_record where id = ?", recordId))
                .as("ending a consent means writing a timestamp, never removing the record")
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("cannot be deleted");

        assertThat(jdbc.queryForObject("select count(*) from consent_record where id = ?",
                Integer.class, recordId)).isEqualTo(1);
    }

    @Test
    void theCompositeForeignKeyRefusesConsentToTheWrongPurposesWording() {
        UUID accountId = adultAccount();
        UUID researchWording = fixtures.currentTermsId(ConsentPurpose.ACADEMIC_RESEARCH);

        assertThatThrownBy(() -> jdbc.update("""
                insert into consent_record (id, account_id, purpose, terms_version_id, granted_by,
                                            granted_at, evidence)
                values (?, ?, 'LEARNING_DATA_PROCESSING', ?, 'SELF', now(), cast('{}' as jsonb))
                """, UUID.randomUUID(), accountId, researchWording))
                .as("fk_consent_terms is what stops a record from claiming consent to the "
                        + "essential purpose against the text of the research one")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theEmailIndexRefusesASecondAccountOnTheSameAddress() {
        String email = fixtures.uniqueEmail();
        fixtures.insertAccount(email, fixtures.adultDateOfBirth(), AccountStatus.ACTIVE);

        assertThatThrownBy(() ->
                fixtures.insertAccount(email, fixtures.adultDateOfBirth(), AccountStatus.ACTIVE))
                .as("invariant 6")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theEmailIndexFreesTheAddressOfAnAnonymisedAccount() {
        String email = fixtures.uniqueEmail();
        UUID erased = fixtures.insertAccount(email, fixtures.adultDateOfBirth(), AccountStatus.ACTIVE);
        jdbc.update("update account set status = 'ANONYMIZED', anonymized_at = now() where id = ?",
                erased);

        UUID reregistered = fixtures.insertAccount(email, fixtures.adultDateOfBirth(),
                AccountStatus.PENDING_VERIFICATION);

        assertThat(reregistered)
                .as("uniqueness is among the accounts still in use, so an Article 18 erasure does "
                        + "not lock the person out of registering again")
                .isNotEqualTo(erased);
    }

    private UUID adultAccount() {
        return fixtures.insertAccount(fixtures.uniqueEmail(), fixtures.adultDateOfBirth(),
                AccountStatus.ACTIVE);
    }

    private UUID insertConsent(UUID accountId, ConsentPurpose purpose) {
        return fixtures.insertConsent(accountId, purpose, ConsentGrantedBy.SELF, null, clock.instant());
    }
}
