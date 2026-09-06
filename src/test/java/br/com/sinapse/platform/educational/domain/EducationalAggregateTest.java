package br.com.sinapse.platform.educational.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.sinapse.platform.educational.api.ClassroomStatus;
import br.com.sinapse.platform.educational.api.EnrollmentEndReason;
import br.com.sinapse.platform.educational.api.VisibilityScope;
import br.com.sinapse.platform.educational.internal.domain.Classroom;
import br.com.sinapse.platform.educational.internal.domain.Enrollment;
import br.com.sinapse.platform.educational.internal.domain.Invite;
import br.com.sinapse.platform.educational.internal.error.ClassroomAlreadyArchivedException;
import br.com.sinapse.platform.educational.internal.error.EnrollmentAlreadyEndedException;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The rules the aggregates hold on their own, without a database in the way. */
class EducationalAggregateTest {

    private static final Instant NOW = Instant.parse("2026-09-06T12:00:00Z");

    @Test
    void anInviteIsGoodUntilItExpires() {
        Invite invite = invite(NOW.plusSeconds(3600), null);

        assertThat(invite.isRedeemableAt(NOW)).isTrue();
        assertThat(invite.isExpiredAt(NOW.plusSeconds(3599))).isFalse();
        assertThat(invite.isExpiredAt(NOW.plusSeconds(3600)))
                .as("expiry is inclusive: at the instant it expires, it is gone")
                .isTrue();
        assertThat(invite.isRedeemableAt(NOW.plusSeconds(3600))).isFalse();
    }

    @Test
    void anInviteWithALimitIsExhaustedWhenItIsReached() {
        Invite invite = invite(NOW.plusSeconds(3600), 2);

        invite.recordRedemption(NOW);
        assertThat(invite.isExhausted()).isFalse();
        invite.recordRedemption(NOW);

        assertThat(invite.isExhausted()).isTrue();
        assertThat(invite.useCount()).isEqualTo(2);
        assertThatThrownBy(() -> invite.recordRedemption(NOW))
                .as("the count is the record of how many students it let in; letting it pass "
                        + "the limit would make the limit a suggestion")
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void anInviteWithNoLimitIsNeverExhausted() {
        Invite invite = invite(NOW.plusSeconds(3600), null);

        for (int i = 0; i < 50; i++) {
            invite.recordRedemption(NOW);
        }

        assertThat(invite.isExhausted()).isFalse();
        assertThat(invite.createdAt()).isEqualTo(NOW);
    }

    @Test
    void revokingAnInviteKeepsTheFirstInstant() {
        Invite invite = invite(NOW.plusSeconds(3600), null);

        invite.revoke(NOW.plusSeconds(60));
        invite.revoke(NOW.plusSeconds(120));

        assertThat(invite.revokedAt())
                .as("archiving a classroom revokes every outstanding invite and must not care "
                        + "which the teacher had already revoked by hand")
                .isEqualTo(NOW.plusSeconds(60));
        assertThat(invite.isRedeemableAt(NOW.plusSeconds(61))).isFalse();
    }

    @Test
    void anEnrollmentEndsOnceAndRecordsWhy() {
        UUID inviteId = UUID.randomUUID();
        Enrollment enrollment = new Enrollment(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), inviteId, NOW);

        assertThat(enrollment.isActive()).isTrue();
        assertThat(enrollment.inviteId())
                .as("the invite it came from is kept, so a membership can be traced to the code "
                        + "that produced it")
                .isEqualTo(inviteId);

        enrollment.end(NOW.plusSeconds(60), EnrollmentEndReason.STUDENT_LEFT);

        assertThat(enrollment.isActive()).isFalse();
        assertThat(enrollment.endedReason()).isEqualTo(EnrollmentEndReason.STUDENT_LEFT);
        assertThatThrownBy(() -> enrollment.end(NOW.plusSeconds(120),
                EnrollmentEndReason.CLASSROOM_ARCHIVED))
                .as("moving the timestamp would rewrite when the teacher's access actually "
                        + "stopped, which is the one thing this record exists to say")
                .isInstanceOf(EnrollmentAlreadyEndedException.class);
        assertThat(enrollment.endedAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    void aClassroomArchivesOnce() {
        Classroom classroom = new Classroom(UUID.randomUUID(), UUID.randomUUID(), "Turma",
                Set.of(UUID.randomUUID()), NOW);

        assertThat(classroom.isOpen()).isTrue();
        classroom.archive(NOW.plusSeconds(60));

        assertThat(classroom.status()).isEqualTo(ClassroomStatus.ARCHIVED);
        assertThat(classroom.archivedAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(classroom.createdAt()).isEqualTo(NOW);
        assertThatThrownBy(() -> classroom.archive(NOW.plusSeconds(120)))
                .isInstanceOf(ClassroomAlreadyArchivedException.class);
    }

    @Test
    void aScopeLimitedToSubjectsCannotWidenAfterItIsDecided() {
        Set<UUID> mutable = new HashSet<>(Set.of(UUID.randomUUID()));
        VisibilityScope.Subjects scope = new VisibilityScope.Subjects(mutable);

        mutable.add(UUID.randomUUID());

        assertThat(scope.subjectIds())
                .as("a scope is a decision about what may be seen; one that changed under the "
                        + "caller would be no decision at all")
                .hasSize(1);
    }

    private static Invite invite(Instant expiresAt, Integer maxUses) {
        return new Invite(UUID.randomUUID(), UUID.randomUUID(), "ABCDEFGHJK", UUID.randomUUID(),
                NOW, expiresAt, maxUses);
    }
}
