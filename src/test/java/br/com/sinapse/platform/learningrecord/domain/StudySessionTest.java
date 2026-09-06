package br.com.sinapse.platform.learningrecord.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.sinapse.platform.learningrecord.api.DurationSource;
import br.com.sinapse.platform.learningrecord.api.RecallRating;
import br.com.sinapse.platform.learningrecord.api.SessionKind;
import br.com.sinapse.platform.learningrecord.api.SessionSource;
import br.com.sinapse.platform.learningrecord.api.SessionStatus;
import br.com.sinapse.platform.learningrecord.internal.domain.StudySession;
import br.com.sinapse.platform.learningrecord.internal.error.SessionAlreadyClosedException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The rules the aggregate holds on its own, without a database in the way. */
class StudySessionTest {

    private static final Instant NOW = Instant.parse("2026-09-06T12:00:00Z");
    private static final UUID ACCOUNT = UUID.randomUUID();
    private static final UUID TOPIC = UUID.randomUUID();

    @Test
    void theSourceIsDerivedFromThePlannedSession() {
        assertThat(started(null).source()).isEqualTo(SessionSource.SELF_DIRECTED);
        assertThat(started(UUID.randomUUID()).source())
                .as("invariant 5 cannot be broken by an argument in the wrong order if the "
                        + "argument does not exist")
                .isEqualTo(SessionSource.FROM_PLAN);
    }

    /**
     * The requirement of section 8.1, stated as an assertion.
     *
     * <p>A session the student decided on and one taken from the plan differ in two fields
     * and in nothing else. Anything that ever makes them differ further makes off-plan study
     * into second-class evidence, and it is the same evidence.
     */
    @Test
    void aSelfDirectedSessionAndAPlannedOneAreTheSameRecordApartFromTheirSource() {
        UUID plannedSessionId = UUID.randomUUID();
        StudySession selfDirected = started(null);
        StudySession fromPlan = started(plannedSessionId);

        selfDirected.complete(NOW.plusSeconds(1800), RecallRating.GOOD);
        fromPlan.complete(NOW.plusSeconds(1800), RecallRating.GOOD);

        assertThat(fromPlan.plannedSessionId()).isEqualTo(plannedSessionId);
        assertThat(selfDirected.plannedSessionId()).isNull();
        assertThat(fromPlan.source()).isNotEqualTo(selfDirected.source());
        assertThat(selfDirected.kind()).isEqualTo(fromPlan.kind());
        assertThat(selfDirected.status()).isEqualTo(fromPlan.status());
        assertThat(selfDirected.startedAt()).isEqualTo(fromPlan.startedAt());
        assertThat(selfDirected.endedAt()).isEqualTo(fromPlan.endedAt());
        assertThat(selfDirected.actualDurationMinutes()).isEqualTo(fromPlan.actualDurationMinutes());
        assertThat(selfDirected.durationSource()).isEqualTo(fromPlan.durationSource());
        assertThat(selfDirected.recallRating()).isEqualTo(fromPlan.recallRating());
    }

    @Test
    void completingATimedSessionMeasuresTheDuration() {
        StudySession session = started(null);

        session.complete(NOW.plusSeconds(3000), RecallRating.HARD);

        assertThat(session.status()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(session.actualDurationMinutes()).isEqualTo(50);
        assertThat(session.durationSource()).isEqualTo(DurationSource.MEASURED);
        assertThat(session.recallRating()).isEqualTo(RecallRating.HARD);
        assertThat(session.isOpen()).isFalse();
    }

    @Test
    void theMeasuredDurationRoundsRatherThanTruncating() {
        StudySession session = started(null);

        // Fifty minutes and forty seconds. Truncation would shorten every session in the
        // record by up to a minute, and a bias that always points the same way is worse in an
        // evidence store than a rounding error that does not.
        session.complete(NOW.plusSeconds(3040), RecallRating.GOOD);

        assertThat(session.actualDurationMinutes()).isEqualTo(51);
    }

    @Test
    void aDurationTheStudentStatesIsMarkedAsTheirs() {
        StudySession session = started(null);

        session.completeWithReportedDuration(NOW.plusSeconds(3600), 25, RecallRating.EASY);

        assertThat(session.actualDurationMinutes())
                .as("the timer said sixty minutes and the student said twenty-five; the record "
                        + "keeps what the student said, and says who said it")
                .isEqualTo(25);
        assertThat(session.durationSource()).isEqualTo(DurationSource.SELF_REPORTED);
    }

    @Test
    void anAbandonedSessionHasNoRatingAndNoDuration() {
        StudySession session = started(null);

        session.abandon(NOW.plusSeconds(600));

        assertThat(session.status()).isEqualTo(SessionStatus.ABANDONED);
        assertThat(session.endedAt()).isEqualTo(NOW.plusSeconds(600));
        assertThat(session.recallRating())
                .as("invariant 4: a rating exists only on a completed session")
                .isNull();
        assertThat(session.actualDurationMinutes())
                .as("the elapsed time of a session somebody walked away from measures nothing")
                .isNull();
        assertThat(session.durationSource()).isNull();
    }

    @Test
    void aClosedSessionCannotBeClosedAgain() {
        StudySession completed = started(null);
        completed.complete(NOW.plusSeconds(600), RecallRating.GOOD);
        StudySession abandoned = started(null);
        abandoned.abandon(NOW.plusSeconds(600));

        assertThatThrownBy(() -> completed.complete(NOW.plusSeconds(1200), RecallRating.AGAIN))
                .as("invariant 2: correcting a closed session is a new record, not an edit")
                .isInstanceOf(SessionAlreadyClosedException.class);
        assertThatThrownBy(() -> completed.abandon(NOW.plusSeconds(1200)))
                .isInstanceOf(SessionAlreadyClosedException.class);
        assertThatThrownBy(() -> abandoned.complete(NOW.plusSeconds(1200), RecallRating.GOOD))
                .as("an abandoned session cannot acquire a rating afterwards either")
                .isInstanceOf(SessionAlreadyClosedException.class);

        assertThat(completed.recallRating()).isEqualTo(RecallRating.GOOD);
        assertThat(completed.endedAt()).isEqualTo(NOW.plusSeconds(600));
    }

    @Test
    void aSessionCannotEndBeforeItStarted() {
        StudySession session = started(null);

        assertThatThrownBy(() -> session.complete(NOW.minusSeconds(1), RecallRating.GOOD))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(session.isOpen()).isTrue();
    }

    @Test
    void aRetroactiveEntryIsBornClosedAndSelfReported() {
        StudySession session = StudySession.recordRetroactively(UUID.randomUUID(), ACCOUNT, TOPIC,
                null, SessionKind.REVISION, NOW.minusSeconds(7200), 45, RecallRating.GOOD);

        assertThat(session.status()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(session.durationSource())
                .as("decision F5: there is no way to record a duration after the fact without "
                        + "the record saying that is what happened")
                .isEqualTo(DurationSource.SELF_REPORTED);
        assertThat(session.endedAt())
                .as("the end is the start plus the length, so the two numbers the student gave "
                        + "cannot contradict each other")
                .isEqualTo(NOW.minusSeconds(7200).plusSeconds(2700));
        assertThat(session.actualDurationMinutes()).isEqualTo(45);
        assertThat(session.recallRating()).isEqualTo(RecallRating.GOOD);
        assertThat(session.plannedDurationMinutes()).isNull();
    }

    private static StudySession started(UUID plannedSessionId) {
        return StudySession.start(UUID.randomUUID(), ACCOUNT, TOPIC, plannedSessionId,
                SessionKind.STUDY, NOW, 50);
    }
}
