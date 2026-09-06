package br.com.sinapse.platform.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.sinapse.platform.identity.api.AccountRole;
import br.com.sinapse.platform.identity.api.AccountStatus;
import br.com.sinapse.platform.identity.internal.domain.Account;
import br.com.sinapse.platform.identity.internal.error.AccountTransitionException;
import br.com.sinapse.platform.identity.internal.error.DateOfBirthImmutableException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The rules the account aggregate holds on its own. */
class AccountTest {

    private static final Instant NOW = Instant.parse("2026-09-05T12:00:00Z");
    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    @Test
    void registrationStartsAwaitingVerificationAsAStudent() {
        Account account = register(LocalDate.of(2000, 1, 1));

        assertThat(account.status()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
        assertThat(account.roles()).containsExactly(AccountRole.STUDENT);
        assertThat(account.activatedAt()).isNull();
    }

    @Test
    void dateOfBirthCanBeCorrectedBeforeActivation() {
        Account account = register(LocalDate.of(2000, 1, 1));

        account.correctDateOfBirth(LocalDate.of(2000, 1, 2));

        assertThat(account.dateOfBirth()).isEqualTo(LocalDate.of(2000, 1, 2));
    }

    @Test
    void dateOfBirthIsImmutableAfterActivation() {
        Account account = register(LocalDate.of(2000, 1, 1));
        account.activate(NOW);

        assertThatThrownBy(() -> account.correctDateOfBirth(LocalDate.of(2012, 1, 1)))
                .as("invariant 2: otherwise a minor walks through the gate by editing a field")
                .isInstanceOf(DateOfBirthImmutableException.class);
        assertThat(account.dateOfBirth()).isEqualTo(LocalDate.of(2000, 1, 1));
    }

    @Test
    void dateOfBirthStaysImmutableWhileSuspended() {
        Account account = register(LocalDate.of(2000, 1, 1));
        account.activate(NOW);
        account.suspend(NOW);

        assertThatThrownBy(() -> account.correctDateOfBirth(LocalDate.of(2012, 1, 1)))
                .as("the rule is about having been active, not about being active now")
                .isInstanceOf(DateOfBirthImmutableException.class);
    }

    @Test
    void ageIsReadInTheHoldersOwnZone() {
        // Midnight UTC on a birthday is still the day before in São Paulo.
        Account account = register(LocalDate.of(2008, 9, 5));
        Instant birthdayInUtc = Instant.parse("2026-09-05T00:30:00Z");

        assertThat(account.ageAt(birthdayInUtc))
                .as("the holder is 17 where they live, and consent is theirs to give a day later")
                .isEqualTo(17);
        assertThat(account.ageOn(LocalDate.of(2026, 9, 5))).isEqualTo(18);
    }

    @Test
    void activationKeepsTheFirstActivationInstant() {
        Account account = register(LocalDate.of(2000, 1, 1));
        account.activate(NOW);
        account.suspend(NOW.plusSeconds(60));
        account.activate(NOW.plusSeconds(120));

        assertThat(account.activatedAt()).isEqualTo(NOW);
        assertThat(account.suspendedAt()).isNull();
        assertThat(account.status()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void anonymisationIsTerminal() {
        Account account = register(LocalDate.of(2000, 1, 1));
        account.activate(NOW);
        account.anonymize(NOW);

        assertThat(account.status()).isEqualTo(AccountStatus.ANONYMIZED);
        assertThatThrownBy(() -> account.activate(NOW.plusSeconds(1)))
                .isInstanceOf(AccountTransitionException.class);
        assertThatThrownBy(() -> account.suspend(NOW.plusSeconds(1)))
                .isInstanceOf(AccountTransitionException.class);
    }

    @Test
    void anonymisationIsIdempotent() {
        Account account = register(LocalDate.of(2000, 1, 1));
        account.anonymize(NOW);
        account.anonymize(NOW.plusSeconds(60));

        assertThat(account.anonymizedAt())
                .as("an erasure that has to be retried must not move the timestamp")
                .isEqualTo(NOW);
    }

    @Test
    void majorityDateIsDerivedFromTheConfiguredThreshold() {
        Account account = register(LocalDate.of(2008, 3, 10));

        assertThat(account.majorityDate(18)).isEqualTo(LocalDate.of(2026, 3, 10));
        assertThat(account.majorityDate(16)).isEqualTo(LocalDate.of(2024, 3, 10));
    }

    private static Account register(LocalDate dateOfBirth) {
        return Account.register(UUID.randomUUID(), "holder@example.com", "hash", dateOfBirth,
                SAO_PAULO, NOW);
    }
}
