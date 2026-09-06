package br.com.sinapse.platform.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.sinapse.platform.identity.api.ConsentGrantedBy;
import br.com.sinapse.platform.identity.api.ConsentPurpose;
import br.com.sinapse.platform.identity.internal.domain.ConsentEvidence;
import br.com.sinapse.platform.identity.internal.domain.ConsentRecord;
import br.com.sinapse.platform.identity.internal.error.ConsentAlreadyRevokedException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Invariant 4, at the level where the mapping cannot help: the object itself. */
class ConsentRecordTest {

    private static final Instant GRANTED_AT = Instant.parse("2026-09-05T12:00:00Z");

    @Test
    void aGrantedRecordIsValidAndCarriesItsGrantor() {
        ConsentRecord record = self();

        assertThat(record.isValid()).isTrue();
        assertThat(record.grantedBy()).isEqualTo(ConsentGrantedBy.SELF);
        assertThat(record.guardianId()).isNull();
        assertThat(record.revokedAt()).isNull();
    }

    @Test
    void aGuardianGrantCarriesTheGuardian() {
        UUID guardianId = UUID.randomUUID();
        ConsentRecord record = ConsentRecord.grantedByGuardian(UUID.randomUUID(), UUID.randomUUID(),
                ConsentPurpose.LEARNING_DATA_PROCESSING, UUID.randomUUID(), guardianId, GRANTED_AT,
                evidence());

        assertThat(record.grantedBy()).isEqualTo(ConsentGrantedBy.GUARDIAN);
        assertThat(record.guardianId()).isEqualTo(guardianId);
    }

    @Test
    void revocationWritesTheTimestampAndNothingElse() {
        ConsentRecord record = self();
        Instant revokedAt = GRANTED_AT.plusSeconds(3600);

        record.revoke(revokedAt);

        assertThat(record.revokedAt()).isEqualTo(revokedAt);
        assertThat(record.isValid()).isFalse();
        assertThat(record.grantedAt()).isEqualTo(GRANTED_AT);
        assertThat(record.grantedBy()).isEqualTo(ConsentGrantedBy.SELF);
    }

    @Test
    void revokingTwiceIsRefused() {
        ConsentRecord record = self();
        record.revoke(GRANTED_AT.plusSeconds(3600));

        assertThatThrownBy(() -> record.revoke(GRANTED_AT.plusSeconds(7200)))
                .as("invariant 4 allows exactly one write of revoked_at")
                .isInstanceOf(ConsentAlreadyRevokedException.class);
    }

    private static ConsentRecord self() {
        return ConsentRecord.grantedBySelf(UUID.randomUUID(), UUID.randomUUID(),
                ConsentPurpose.LEARNING_DATA_PROCESSING, UUID.randomUUID(), GRANTED_AT, evidence());
    }

    private static ConsentEvidence evidence() {
        return ConsentEvidence.ofApiForm("203.0.113.7", "unit-test");
    }
}
