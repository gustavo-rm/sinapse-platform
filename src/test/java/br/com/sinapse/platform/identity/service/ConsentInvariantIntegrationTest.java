package br.com.sinapse.platform.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.sinapse.platform.identity.api.AccountAccessPolicy;
import br.com.sinapse.platform.identity.api.AccountStatus;
import br.com.sinapse.platform.identity.api.ConsentGrantedBy;
import br.com.sinapse.platform.identity.api.ConsentPurpose;
import br.com.sinapse.platform.identity.internal.domain.Account;
import br.com.sinapse.platform.identity.internal.domain.AccountTokenPurpose;
import br.com.sinapse.platform.identity.internal.domain.ConsentRecord;
import br.com.sinapse.platform.identity.internal.error.ConsentAlreadyGrantedException;
import br.com.sinapse.platform.identity.internal.error.ConsentNotFoundException;
import br.com.sinapse.platform.identity.internal.error.EssentialConsentMissingException;
import br.com.sinapse.platform.identity.internal.error.GuardianRequiredException;
import br.com.sinapse.platform.identity.internal.error.MinorRegistrationNotSupportedException;
import br.com.sinapse.platform.identity.internal.persistence.ConsentRecordRepository;
import br.com.sinapse.platform.identity.internal.service.ConsentService;
import br.com.sinapse.platform.identity.internal.service.EmailVerificationService;
import br.com.sinapse.platform.identity.support.IdentityFixtures;
import br.com.sinapse.platform.identity.support.IdentityIntegrationTest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The invariants of section 5.3, each one exercised by making it fail.
 *
 * <p>An invariant that has never rejected anything is a comment.
 */
class ConsentInvariantIntegrationTest extends IdentityIntegrationTest {

    @Autowired
    private ConsentService consents;

    @Autowired
    private EmailVerificationService verification;

    @Autowired
    private ConsentRecordRepository records;

    @Autowired
    private AccountAccessPolicy accessPolicy;

    @Autowired
    private Clock clock;

    @Test
    void anAdultBecomesActiveAsSoonAsTheAddressIsVerified() {
        Account registered = fixtures.registerAdult(fixtures.uniqueEmail());
        assertThat(registered.status()).isEqualTo(AccountStatus.PENDING_VERIFICATION);

        verification.verify(notifications.requireToken(registered.id(),
                AccountTokenPurpose.EMAIL_VERIFICATION));

        assertThat(fixtures.reload(registered.id()).status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(accessPolicy.canProcessLearningData(registered.id())).isTrue();
    }

    @Test
    void activationIsRefusedWithoutAConsentForEveryEssentialPurpose() {
        UUID accountId = fixtures.insertAccount(fixtures.uniqueEmail(), fixtures.adultDateOfBirth(),
                AccountStatus.PENDING_VERIFICATION);

        assertThatThrownBy(() -> consents.activateAfterEmailVerification(accountId))
                .as("invariant 1: an active account has valid essential consent, or it is not active")
                .isInstanceOf(EssentialConsentMissingException.class);
        assertThat(fixtures.reload(accountId).status()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
    }

    @Test
    void activationIsRefusedWhenTheGrantorDoesNotMatchTheAgeAtTheTime() {
        LocalDate dateOfBirth = fixtures.adultDateOfBirth();
        UUID accountId = fixtures.insertAccount(fixtures.uniqueEmail(), dateOfBirth,
                AccountStatus.PENDING_VERIFICATION);
        UUID guardianId = fixtures.insertGuardian(accountId, true);
        fixtures.insertConsent(accountId, ConsentPurpose.LEARNING_DATA_PROCESSING,
                ConsentGrantedBy.GUARDIAN, guardianId, clock.instant());

        assertThatThrownBy(() -> consents.activateAfterEmailVerification(accountId))
                .as("the second half of invariant 1: a valid consent whose grantor was not "
                        + "entitled to give it is not a consent this account may be activated on")
                .isInstanceOf(EssentialConsentMissingException.class);
    }

    @Test
    void aHolderBelowTheThresholdIsRefusedAtRegistration() {
        LocalDate seventeen = LocalDate.ofInstant(clock.instant(), IdentityFixtures.DEFAULT_ZONE)
                .minusYears(17);

        assertThatThrownBy(() -> fixtures.registerAdult(fixtures.uniqueEmail(), seventeen, false))
                .as("v1 implements the SELF branch only, and refuses rather than half-building "
                        + "the guardian one")
                .isInstanceOf(MinorRegistrationNotSupportedException.class);
    }

    @Test
    void aHolderBelowTheThresholdCannotConsentWithoutAGuardian() {
        LocalDate seventeen = LocalDate.ofInstant(clock.instant(), IdentityFixtures.DEFAULT_ZONE)
                .minusYears(17);
        UUID accountId = fixtures.insertAccount(fixtures.uniqueEmail(), seventeen,
                AccountStatus.PENDING_VERIFICATION);

        assertThatThrownBy(() -> consents.grant(accountId, ConsentPurpose.LEARNING_DATA_PROCESSING,
                fixtures.currentTermsId(ConsentPurpose.LEARNING_DATA_PROCESSING), fixtures.evidence()))
                .as("invariant 3, forwards")
                .isInstanceOf(GuardianRequiredException.class);
    }

    @Test
    void aHolderAboveTheThresholdConsentsInTheirOwnNameEvenWithAGuardianOnRecord() {
        UUID accountId = fixtures.insertAccount(fixtures.uniqueEmail(), fixtures.adultDateOfBirth(),
                AccountStatus.PENDING_VERIFICATION);
        fixtures.insertGuardian(accountId, true);

        ConsentRecord record = consents.grant(accountId, ConsentPurpose.LEARNING_DATA_PROCESSING,
                fixtures.currentTermsId(ConsentPurpose.LEARNING_DATA_PROCESSING), fixtures.evidence());

        assertThat(record.grantedBy())
                .as("invariant 3 is about the record. The guardian who answered for this holder "
                        + "as a minor stays on file, because that act happened; what may not "
                        + "happen is a new consent in their name.")
                .isEqualTo(ConsentGrantedBy.SELF);
        assertThat(record.guardianId()).isNull();
    }

    @Test
    void aHolderBelowTheThresholdCannotReaffirmInTheirOwnName() {
        LocalDate seventeen = LocalDate.ofInstant(clock.instant(), IdentityFixtures.DEFAULT_ZONE)
                .minusYears(17);
        UUID accountId = fixtures.insertAccount(fixtures.uniqueEmail(), seventeen, AccountStatus.ACTIVE);
        UUID guardianId = fixtures.insertGuardian(accountId, true);
        fixtures.insertConsent(accountId, ConsentPurpose.LEARNING_DATA_PROCESSING,
                ConsentGrantedBy.GUARDIAN, guardianId, clock.instant());

        assertThatThrownBy(() -> consents.reaffirmMajority(accountId, fixtures.evidence()))
                .as("invariant 3, backwards: below the threshold, consent is still the "
                        + "guardian's to give")
                .isInstanceOf(GuardianRequiredException.class);
    }

    @Test
    void aMinorWhoseGuardianConsentedButWasNeverVerifiedWaits() {
        LocalDate seventeen = LocalDate.ofInstant(clock.instant(), IdentityFixtures.DEFAULT_ZONE)
                .minusYears(17);
        UUID accountId = fixtures.insertAccount(fixtures.uniqueEmail(), seventeen,
                AccountStatus.PENDING_VERIFICATION);
        UUID guardianId = fixtures.insertGuardian(accountId, false);
        fixtures.insertConsent(accountId, ConsentPurpose.LEARNING_DATA_PROCESSING,
                ConsentGrantedBy.GUARDIAN, guardianId, clock.instant());

        consents.activateAfterEmailVerification(accountId);

        assertThat(fixtures.reload(accountId).status())
                .as("a minor does not become active on a guardian consent nobody confirmed, and "
                        + "in this version nothing confirms one")
                .isEqualTo(AccountStatus.PENDING_GUARDIAN_CONSENT);
        assertThat(accessPolicy.canProcessLearningData(accountId)).isFalse();
    }

    @Test
    void revokingAnEssentialPurposeSuspendsTheAccountInTheSameTransaction() {
        Account account = fixtures.activeAdult(fixtures.uniqueEmail());

        consents.revoke(account.id(), ConsentPurpose.LEARNING_DATA_PROCESSING);

        assertThat(fixtures.reload(account.id()).status())
                .as("invariant 5")
                .isEqualTo(AccountStatus.SUSPENDED);
        assertThat(accessPolicy.canProcessLearningData(account.id())).isFalse();

        List<Object> stillValid = jdbc.queryForList(
                "select id from consent_record where account_id = ? and revoked_at is null",
                Object.class, account.id());
        assertThat(stillValid).isEmpty();
    }

    @Test
    void revokingTheResearchPurposeChangesNothingElse() {
        Account account = fixtures.activeAdult(fixtures.uniqueEmail(), true);
        assertThat(accessPolicy.canUseForResearch(account.id())).isTrue();

        consents.revoke(account.id(), ConsentPurpose.ACADEMIC_RESEARCH);

        assertThat(fixtures.reload(account.id()).status())
                .as("refusing research has to cost the holder nothing, or the consent is not free "
                        + "and the data is not publishable")
                .isEqualTo(AccountStatus.ACTIVE);
        assertThat(accessPolicy.canUseForResearch(account.id())).isFalse();
        assertThat(accessPolicy.canProcessLearningData(account.id())).isTrue();
    }

    @Test
    void revokingKeepsTheRecordAndAddsATimestamp() {
        Account account = fixtures.activeAdult(fixtures.uniqueEmail(), true);
        UUID recordId = records
                .findByAccountIdAndPurposeAndRevokedAtIsNull(account.id(), ConsentPurpose.ACADEMIC_RESEARCH)
                .orElseThrow()
                .id();

        consents.revoke(account.id(), ConsentPurpose.ACADEMIC_RESEARCH);

        assertThat(records.findById(recordId)).hasValueSatisfying(record -> {
            assertThat(record.revokedAt()).isNotNull();
            assertThat(record.grantedAt()).isNotNull();
            assertThat(record.evidence()).isNotNull();
        });
    }

    @Test
    void aPurposeCannotBeConsentedToTwiceWhileTheFirstConsentStands() {
        Account account = fixtures.activeAdult(fixtures.uniqueEmail());
        UUID termsId = fixtures.currentTermsId(ConsentPurpose.INSTITUTION_SHARING);
        consents.grant(account.id(), ConsentPurpose.INSTITUTION_SHARING, termsId, fixtures.evidence());

        assertThatThrownBy(() -> consents.grant(account.id(), ConsentPurpose.INSTITUTION_SHARING,
                termsId, fixtures.evidence()))
                .isInstanceOf(ConsentAlreadyGrantedException.class);
    }

    @Test
    void withdrawingAPurposeThatWasNeverConsentedToIsRefused() {
        Account account = fixtures.activeAdult(fixtures.uniqueEmail());

        assertThatThrownBy(() -> consents.revoke(account.id(), ConsentPurpose.ACADEMIC_RESEARCH))
                .isInstanceOf(ConsentNotFoundException.class);
    }

    @Test
    void anAnonymisedAccountCannotConsentAgain() {
        Account account = fixtures.activeAdult(fixtures.uniqueEmail());
        consents.anonymize(account.id());

        Instant before = clock.instant();
        assertThatThrownBy(() -> consents.grant(account.id(), ConsentPurpose.ACADEMIC_RESEARCH,
                fixtures.currentTermsId(ConsentPurpose.ACADEMIC_RESEARCH), fixtures.evidence()))
                .isInstanceOf(RuntimeException.class);
        assertThat(fixtures.reload(account.id()).anonymizedAt()).isBeforeOrEqualTo(before);
    }
}
