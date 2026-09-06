package br.com.sinapse.platform.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.sinapse.platform.identity.internal.domain.UserSession;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The two expiries of ADR 0010, and the revocation that has to be immediate. */
class UserSessionTest {

    private static final Instant OPENED_AT = Instant.parse("2026-09-05T12:00:00Z");
    private static final Duration IDLE = Duration.ofDays(7);
    private static final Duration ABSOLUTE = Duration.ofDays(30);

    @Test
    void aFreshSessionIsUsable() {
        assertThat(session().isUsableAt(OPENED_AT.plusSeconds(60), IDLE)).isTrue();
    }

    @Test
    void idlingPastTheTimeoutEndsIt() {
        UserSession session = session();

        assertThat(session.isUsableAt(OPENED_AT.plus(IDLE).minusSeconds(1), IDLE)).isTrue();
        assertThat(session.isUsableAt(OPENED_AT.plus(IDLE), IDLE)).isFalse();
    }

    @Test
    void useResetsTheIdleWindowButNotTheAbsoluteOne() {
        UserSession session = session();
        Instant sixDaysIn = OPENED_AT.plus(Duration.ofDays(6));

        session.touch(sixDaysIn);

        assertThat(session.isUsableAt(sixDaysIn.plus(Duration.ofDays(6)), IDLE))
                .as("activity keeps it alive")
                .isTrue();
        assertThat(session.isUsableAt(OPENED_AT.plus(ABSOLUTE), IDLE))
                .as("the absolute expiry is never extended, however active the session is")
                .isFalse();
    }

    @Test
    void revocationTakesEffectImmediately() {
        UserSession session = session();

        session.revoke(OPENED_AT.plusSeconds(60));

        assertThat(session.isUsableAt(OPENED_AT.plusSeconds(61), IDLE))
                .as("this is what a stateless token could not do, and the reason ADR 0010 "
                        + "chose a server-side session")
                .isFalse();
    }

    @Test
    void revocationKeepsTheFirstInstant() {
        UserSession session = session();
        session.revoke(OPENED_AT.plusSeconds(60));
        session.revoke(OPENED_AT.plusSeconds(120));

        assertThat(session.revokedAt()).isEqualTo(OPENED_AT.plusSeconds(60));
    }

    private static UserSession session() {
        return new UserSession(UUID.randomUUID(), UUID.randomUUID(), "hash", OPENED_AT,
                OPENED_AT.plus(ABSOLUTE), "agent", "ip-hash");
    }
}
