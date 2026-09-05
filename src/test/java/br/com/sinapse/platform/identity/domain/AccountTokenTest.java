package br.com.sinapse.platform.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.sinapse.platform.identity.internal.domain.AccountToken;
import br.com.sinapse.platform.identity.internal.domain.AccountTokenPurpose;
import br.com.sinapse.platform.identity.internal.error.InvalidTokenException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Single use, expiring, and only for what it was issued for. */
class AccountTokenTest {

    private static final Instant ISSUED_AT = Instant.parse("2026-09-05T12:00:00Z");
    private static final Instant EXPIRES_AT = ISSUED_AT.plusSeconds(3600);

    @Test
    void consumingMarksTheInstant() {
        AccountToken token = token(AccountTokenPurpose.EMAIL_VERIFICATION);

        token.consume(AccountTokenPurpose.EMAIL_VERIFICATION, ISSUED_AT.plusSeconds(60));

        assertThat(token.consumedAt()).isEqualTo(ISSUED_AT.plusSeconds(60));
    }

    @Test
    void aConsumedTokenIsRefused() {
        AccountToken token = token(AccountTokenPurpose.EMAIL_VERIFICATION);
        token.consume(AccountTokenPurpose.EMAIL_VERIFICATION, ISSUED_AT.plusSeconds(60));

        assertThatThrownBy(() -> token.consume(AccountTokenPurpose.EMAIL_VERIFICATION,
                ISSUED_AT.plusSeconds(120)))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void anExpiredTokenIsRefused() {
        AccountToken token = token(AccountTokenPurpose.PASSWORD_RESET);

        assertThatThrownBy(() -> token.consume(AccountTokenPurpose.PASSWORD_RESET, EXPIRES_AT))
                .as("expiry is inclusive: at the instant it expires, it is gone")
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void aTokenIssuedForAnotherPurposeIsRefused() {
        AccountToken token = token(AccountTokenPurpose.PASSWORD_RESET);

        assertThatThrownBy(() -> token.consume(AccountTokenPurpose.EMAIL_VERIFICATION,
                ISSUED_AT.plusSeconds(60)))
                .as("otherwise a reset token verifies an address, or worse the other way round")
                .isInstanceOf(InvalidTokenException.class);
        assertThat(token.consumedAt()).isNull();
    }

    private static AccountToken token(AccountTokenPurpose purpose) {
        return new AccountToken(UUID.randomUUID(), UUID.randomUUID(), purpose, "hash",
                ISSUED_AT, EXPIRES_AT);
    }
}
