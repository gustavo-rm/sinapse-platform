package br.com.sinapse.platform.identity.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.sinapse.platform.identity.api.AccountStatus;
import br.com.sinapse.platform.identity.api.ConsentGrantedBy;
import br.com.sinapse.platform.identity.api.ConsentPurpose;
import br.com.sinapse.platform.identity.internal.domain.Account;
import br.com.sinapse.platform.identity.internal.domain.AccountTokenPurpose;
import br.com.sinapse.platform.identity.internal.domain.ConsentRecord;
import br.com.sinapse.platform.identity.internal.persistence.ConsentRecordRepository;
import br.com.sinapse.platform.identity.internal.service.MajorityReaffirmationService;
import br.com.sinapse.platform.identity.internal.service.MajorityTransitionService;
import br.com.sinapse.platform.identity.support.IdentityFixtures;
import br.com.sinapse.platform.identity.support.IdentityIntegrationTest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The majority transition of section 5.6.
 *
 * <p>The rule is not to block anyone on their birthday: the consent a guardian gave was
 * validly obtained and does not become void at that instant, so the holder is asked, keeps
 * the account for thirty days, and is suspended only if the period runs out unanswered.
 *
 * <p>The sweep takes the instant to evaluate as an argument, which is what makes these tests
 * possible without waiting eighteen years or moving a clock.
 */
class MajorityTransitionIntegrationTest extends IdentityIntegrationTest {

    private static final int THRESHOLD = 18;
    private static final int GRACE_DAYS = 30;
    private static final ZoneId ZONE = IdentityFixtures.DEFAULT_ZONE;

    @Autowired
    private MajorityTransitionService sweep;

    @Autowired
    private MajorityReaffirmationService reaffirmation;

    @Autowired
    private ConsentRecordRepository records;

    @Autowired
    private Clock clock;

    @Test
    void anAccountInsideTheGracePeriodStaysUsableAndIsAskedOnce() {
        Instant now = clock.instant();
        UUID accountId = holderWhoReachedTheThreshold(now, 5);

        MajorityTransitionService.SweepResult first = sweep.sweep(now);
        MajorityTransitionService.SweepResult second = sweep.sweep(now);

        assertThat(first.notified()).isEqualTo(1);
        assertThat(first.suspended()).isZero();
        assertThat(second.notified())
                .as("the sweep sees the same account every day of the grace period; without the "
                        + "outstanding-token check it would send thirty messages")
                .isZero();
        assertThat(fixtures.reload(accountId).status())
                .as("the account is not blocked on the birthday")
                .isEqualTo(AccountStatus.ACTIVE);
        assertThat(notifications.deliveredTo(accountId, AccountTokenPurpose.MAJORITY_REAFFIRMATION))
                .hasSize(1);
    }

    @Test
    void anAccountOnTheLastDayOfTheGracePeriodIsStillUsable() {
        Instant now = clock.instant();
        UUID accountId = holderWhoReachedTheThreshold(now, GRACE_DAYS);

        MajorityTransitionService.SweepResult result = sweep.sweep(now);

        assertThat(result.suspended()).isZero();
        assertThat(fixtures.reload(accountId).status()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void anAccountPastTheGracePeriodIsSuspended() {
        Instant now = clock.instant();
        UUID accountId = holderWhoReachedTheThreshold(now, GRACE_DAYS + 1);

        MajorityTransitionService.SweepResult result = sweep.sweep(now);

        assertThat(result.suspended()).isEqualTo(1);
        assertThat(fixtures.reload(accountId).status()).isEqualTo(AccountStatus.SUSPENDED);
    }

    @Test
    void reaffirmingInsideTheGracePeriodWritesANewRecordAndPreservesThePrevious() {
        Instant now = clock.instant();
        UUID accountId = holderWhoReachedTheThreshold(now, 5);
        UUID previous = validEssentialConsent(accountId).id();

        reaffirmation.reaffirm(accountId, fixtures.evidence());

        ConsentRecord current = validEssentialConsent(accountId);
        assertThat(current.id()).isNotEqualTo(previous);
        assertThat(current.grantedBy()).isEqualTo(ConsentGrantedBy.SELF);
        assertThat(current.guardianId()).isNull();

        assertThat(records.findById(previous)).hasValueSatisfying(old -> {
            assertThat(old.grantedBy())
                    .as("the guardian's act is not rewritten; it is closed and kept")
                    .isEqualTo(ConsentGrantedBy.GUARDIAN);
            assertThat(old.revokedAt()).isNotNull();
        });

        assertThat(fixtures.reload(accountId).status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(sweep.sweep(now))
                .as("nothing further is owed, so the next pass leaves the account alone")
                .isEqualTo(new MajorityTransitionService.SweepResult(0, 0));
    }

    @Test
    void reaffirmingWithTheTokenTheSweepIssuedWorksWithoutASession() {
        Instant now = clock.instant();
        UUID accountId = holderWhoReachedTheThreshold(now, 5);
        sweep.sweep(now);
        String token = notifications.requireToken(accountId, AccountTokenPurpose.MAJORITY_REAFFIRMATION);

        reaffirmation.reaffirmWithToken(token, fixtures.evidence());

        assertThat(validEssentialConsent(accountId).grantedBy()).isEqualTo(ConsentGrantedBy.SELF);
        assertThat(fixtures.reload(accountId).status()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void anAccountOpenedInAdulthoodIsNeverAskedToReaffirm() {
        Account account = fixtures.activeAdult(fixtures.uniqueEmail());

        MajorityTransitionService.SweepResult result = sweep.sweep(clock.instant());

        assertThat(result)
                .as("the condition is satisfied trivially: the holder consented in their own name, "
                        + "after their own eighteenth birthday")
                .isEqualTo(new MajorityTransitionService.SweepResult(0, 0));
        assertThat(fixtures.reload(account.id()).status()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void aHolderBelowTheThresholdIsLeftAlone() {
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, ZONE);
        LocalDate dateOfBirth = today.minusYears(THRESHOLD).plusDays(10);
        UUID accountId = fixtures.insertAccountConsentedByGuardian(
                fixtures.uniqueEmail(), dateOfBirth, now.minusSeconds(86400));

        MajorityTransitionService.SweepResult result = sweep.sweep(now);

        assertThat(result).isEqualTo(new MajorityTransitionService.SweepResult(0, 0));
        assertThat(fixtures.reload(accountId).status()).isEqualTo(AccountStatus.ACTIVE);
    }

    /**
     * An account whose holder reached the threshold a given number of days ago, on a consent
     * a guardian gave while they were still below it.
     */
    private UUID holderWhoReachedTheThreshold(Instant now, int daysAgo) {
        LocalDate today = LocalDate.ofInstant(now, ZONE);
        LocalDate dateOfBirth = today.minusYears(THRESHOLD).minusDays(daysAgo);
        Instant consentedAt = dateOfBirth.plusYears(THRESHOLD - 1).atStartOfDay(ZONE).toInstant();
        return fixtures.insertAccountConsentedByGuardian(fixtures.uniqueEmail(), dateOfBirth,
                consentedAt);
    }

    private ConsentRecord validEssentialConsent(UUID accountId) {
        List<ConsentRecord> valid = records.findByAccountIdAndRevokedAtIsNull(accountId).stream()
                .filter(record -> record.purpose().isEssential())
                .toList();
        assertThat(valid).hasSize(1);
        return valid.get(0);
    }
}
