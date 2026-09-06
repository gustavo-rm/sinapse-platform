package br.com.sinapse.platform.learningrecord.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.sinapse.platform.curriculum.api.TopicView;
import br.com.sinapse.platform.identity.api.ConsentPurpose;
import br.com.sinapse.platform.identity.internal.domain.Account;
import br.com.sinapse.platform.identity.internal.service.ConsentService;
import br.com.sinapse.platform.learningrecord.api.DurationSource;
import br.com.sinapse.platform.learningrecord.api.RecallRating;
import br.com.sinapse.platform.learningrecord.api.SessionKind;
import br.com.sinapse.platform.learningrecord.api.SessionSource;
import br.com.sinapse.platform.learningrecord.api.SessionStatus;
import br.com.sinapse.platform.learningrecord.api.StudySessionView;
import br.com.sinapse.platform.learningrecord.internal.error.InvalidTimeWindowException;
import br.com.sinapse.platform.learningrecord.internal.error.LearningDataNotProcessableException;
import br.com.sinapse.platform.learningrecord.internal.error.SessionAlreadyClosedException;
import br.com.sinapse.platform.learningrecord.internal.error.SessionAlreadyOpenException;
import br.com.sinapse.platform.learningrecord.internal.error.UnknownSessionException;
import br.com.sinapse.platform.learningrecord.internal.error.UnknownTopicException;
import br.com.sinapse.platform.learningrecord.support.LearningRecordIntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** The session lifecycle, through the services that own it. */
class StudySessionLifecycleIntegrationTest extends LearningRecordIntegrationTest {

    /** Generous: the second transaction only has to wait for the first to release the index. */
    private static final int TIMEOUT_SECONDS = 30;

    @Autowired
    private ConsentService consents;

    @Test
    void aTimedSessionIsStartedAndCompleted() {
        Account student = student();
        TopicView topic = topic();

        StudySessionView started = sessions.start(student.id(), topic.id(), null,
                SessionKind.STUDY, 50);

        assertThat(started.status()).isEqualTo(SessionStatus.IN_PROGRESS);
        assertThat(started.source()).isEqualTo(SessionSource.SELF_DIRECTED);
        assertThat(started.plannedDurationMinutes()).isEqualTo(50);
        assertThat(started.endedAt()).isNull();
        assertThat(started.effectiveDuration())
                .as("a duration that grew every time it was read would not be evidence")
                .isEmpty();

        StudySessionView completed = sessions.complete(student.id(), started.id(),
                RecallRating.GOOD, null);

        assertThat(completed.status()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(completed.durationSource()).isEqualTo(DurationSource.MEASURED);
        assertThat(completed.recallRating()).isEqualTo(RecallRating.GOOD);
        assertThat(completed.effectiveDuration()).isPresent();
        assertThat(columnOf(started.id(), "status", String.class)).isEqualTo("COMPLETED");
    }

    @Test
    void aDurationSuppliedAtTheCloseIsRecordedAsTheStudentsOwn() {
        Account student = student();
        StudySessionView started = sessions.start(student.id(), topic().id(), null,
                SessionKind.STUDY, 50);

        StudySessionView completed = sessions.complete(student.id(), started.id(),
                RecallRating.HARD, 35);

        assertThat(completed.actualDurationMinutes()).isEqualTo(35);
        assertThat(completed.durationSource())
                .as("decision F5: the two origins are not equally reliable, so they have to be "
                        + "distinguishable in the data rather than silently mixed")
                .isEqualTo(DurationSource.SELF_REPORTED);
    }

    @Test
    void anAbandonedSessionKeepsNoRatingAndNoDuration() {
        Account student = student();
        StudySessionView started = sessions.start(student.id(), topic().id(), null,
                SessionKind.STUDY, null);

        StudySessionView abandoned = sessions.abandon(student.id(), started.id());

        assertThat(abandoned.status()).isEqualTo(SessionStatus.ABANDONED);
        assertThat(abandoned.recallRating()).isNull();
        assertThat(abandoned.actualDurationMinutes()).isNull();
        assertThat(abandoned.durationSource()).isNull();
    }

    @Test
    void aSecondSessionIsRefusedWhileOneIsRunning() {
        Account student = student();
        TopicView first = topic();
        TopicView second = topic();
        sessions.start(student.id(), first.id(), null, SessionKind.STUDY, 50);

        assertThatThrownBy(() -> sessions.start(student.id(), second.id(), null,
                SessionKind.STUDY, 50))
                .as("invariant 1. The screen is expected to offer resuming the open session")
                .isInstanceOf(SessionAlreadyOpenException.class);

        assertThat(sessionCount(student.id())).isEqualTo(1);
    }

    @Test
    void aNewSessionIsAcceptedOnceTheRunningOneIsClosed() {
        Account student = student();
        StudySessionView first = sessions.start(student.id(), topic().id(), null,
                SessionKind.STUDY, 50);
        sessions.abandon(student.id(), first.id());

        assertThatCode(() -> sessions.start(student.id(), topic().id(), null, SessionKind.STUDY,
                50)).doesNotThrowAnyException();
        assertThat(sessionCount(student.id())).isEqualTo(2);
    }

    @Test
    void aClosedSessionCannotBeClosedAgain() {
        Account student = student();
        StudySessionView started = sessions.start(student.id(), topic().id(), null,
                SessionKind.STUDY, 50);
        sessions.complete(student.id(), started.id(), RecallRating.EASY, null);

        assertThatThrownBy(() -> sessions.complete(student.id(), started.id(), RecallRating.AGAIN,
                null))
                .as("invariant 2. A correction is a new record; the trigger refuses the update "
                        + "underneath this in any case")
                .isInstanceOf(SessionAlreadyClosedException.class);

        assertThat(columnOf(started.id(), "recall_rating", String.class)).isEqualTo("EASY");
    }

    @Test
    void aSessionOfAnotherAccountIsNotVisibleAndNotClosable() {
        Account owner = student();
        Account other = student();
        StudySessionView started = sessions.start(owner.id(), topic().id(), null,
                SessionKind.STUDY, 50);

        assertThatThrownBy(() -> sessions.complete(other.id(), started.id(), RecallRating.GOOD,
                null))
                .as("a session that exists and a session that belongs to somebody else answer "
                        + "the same way; distinguishing them would disclose the first fact")
                .isInstanceOf(UnknownSessionException.class);
        assertThatThrownBy(() -> sessions.abandon(other.id(), started.id()))
                .isInstanceOf(UnknownSessionException.class);
    }

    @Test
    void aTopicOutsideTheCatalogueIsRefused() {
        Account student = student();

        assertThatThrownBy(() -> sessions.start(student.id(), UUID.randomUUID(), null,
                SessionKind.STUDY, 50))
                .isInstanceOf(UnknownTopicException.class);
    }

    /**
     * The access gate, exercised through the one thing that can close it in the v1.
     *
     * <p>Withdrawing the essential consent suspends the account in the same transaction, and
     * from that moment the learning data may not be processed at all. Nothing in this module
     * reads a status to reach that conclusion.
     */
    @Test
    void anAccountWhoseDataMayNotBeProcessedRecordsNothing() {
        Account student = student();
        TopicView topic = topic();
        StudySessionView open = sessions.start(student.id(), topic.id(), null, SessionKind.STUDY,
                50);

        consents.revoke(student.id(), ConsentPurpose.LEARNING_DATA_PROCESSING);

        assertThatThrownBy(() -> sessions.start(student.id(), topic.id(), null, SessionKind.STUDY,
                50)).isInstanceOf(LearningDataNotProcessableException.class);
        assertThatThrownBy(() -> sessions.complete(student.id(), open.id(), RecallRating.GOOD,
                null)).isInstanceOf(LearningDataNotProcessableException.class);
        assertThatThrownBy(() -> sessions.recordRetroactively(student.id(), topic.id(), null,
                SessionKind.STUDY, clock.instant().minus(Duration.ofHours(2)), 45,
                RecallRating.GOOD))
                .isInstanceOf(LearningDataNotProcessableException.class);

        assertThat(columnOf(open.id(), "status", String.class))
                .as("the session that was already running is left exactly as it was: the record "
                        + "is not rewritten by a withdrawal")
                .isEqualTo("IN_PROGRESS");
    }

    /**
     * Section 8.1, checked against what actually reaches the table.
     *
     * <p>The aggregate test makes the same claim in memory. This one makes it about the row,
     * because the requirement is about the record and a mapping is what turns one into the
     * other.
     */
    @Test
    void aSpontaneousSessionAndAPlannedOneProduceTheSameRecordApartFromTheirSource() {
        Account student = student();
        TopicView topic = topic();
        UUID plannedSessionId = UUID.randomUUID();

        StudySessionView spontaneous = complete(student, topic, null);
        StudySessionView fromPlan = complete(student, topic, plannedSessionId);

        assertThat(fromPlan.source()).isEqualTo(SessionSource.FROM_PLAN);
        assertThat(spontaneous.source()).isEqualTo(SessionSource.SELF_DIRECTED);
        assertThat(fromPlan.plannedSessionId()).isEqualTo(plannedSessionId);
        assertThat(spontaneous.plannedSessionId()).isNull();

        assertThat(rowOf(spontaneous.id()))
                .as("the two rows differ in source and in the planned session, and in nothing "
                        + "else. A second shape would make off-plan study second-class evidence")
                .isEqualTo(rowOf(fromPlan.id()));
    }

    @Test
    void aRetroactiveEntryIsRecordedAsSelfReportedAndDoesNotOccupyTheRunningSlot() {
        Account student = student();
        TopicView topic = topic();
        StudySessionView running = sessions.start(student.id(), topic.id(), null,
                SessionKind.STUDY, 50);

        StudySessionView recorded = sessions.recordRetroactively(student.id(), topic.id(), null,
                SessionKind.REVISION, clock.instant().minus(Duration.ofDays(2)), 45,
                RecallRating.HARD);

        assertThat(recorded.status()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(recorded.durationSource()).isEqualTo(DurationSource.SELF_REPORTED);
        assertThat(recorded.actualDurationMinutes()).isEqualTo(45);
        assertThat(columnOf(running.id(), "status", String.class))
                .as("a session entered after the fact is born closed, so yesterday's entry does "
                        + "not disturb today's timer")
                .isEqualTo("IN_PROGRESS");
    }

    @Test
    void aRetroactiveEntryOutsideTheAcceptedRangeIsRefused() {
        Account student = student();
        TopicView topic = topic();
        Instant now = clock.instant();

        assertThatThrownBy(() -> sessions.recordRetroactively(student.id(), topic.id(), null,
                SessionKind.STUDY, now.minus(Duration.ofDays(30)), 45, RecallRating.GOOD))
                .as("without a bound this route is a way of writing a study history that was "
                        + "never studied")
                .isInstanceOf(InvalidTimeWindowException.class);

        assertThatThrownBy(() -> sessions.recordRetroactively(student.id(), topic.id(), null,
                SessionKind.STUDY, now.plus(Duration.ofHours(1)), 45, RecallRating.GOOD))
                .as("a session cannot already have happened in the future")
                .isInstanceOf(InvalidTimeWindowException.class);

        assertThatThrownBy(() -> sessions.recordRetroactively(student.id(), topic.id(), null,
                SessionKind.STUDY, now.minus(Duration.ofMinutes(10)), 45, RecallRating.GOOD))
                .as("nor can it still be running: ten minutes ago plus forty-five minutes has "
                        + "not happened yet")
                .isInstanceOf(InvalidTimeWindowException.class);

        assertThat(sessionCount(student.id())).isZero();
    }

    /**
     * Two requests to start a session, at the same time, for the same account.
     *
     * <p>The test invariant 1 exists for. Under read committed neither transaction sees the
     * other's uncommitted row, so the lookup the service makes first passes in both — which is
     * precisely why the guarantee is a partial unique index and not a check in code. One
     * insert lands and the other is refused by the database, and the refusal reaches the
     * caller as the same conflict the lookup would have produced rather than as a failure
     * nobody anticipated.
     *
     * <p>Which of the two loses is not determined, and the assertion does not care. What it
     * asserts is that exactly one does, and that the account is left with one session.
     */
    @Test
    void twoSimultaneousStartsLeaveExactlyOneSessionRunning() throws Exception {
        Account student = student();
        TopicView first = topic();
        TopicView second = topic();
        CyclicBarrier bothReady = new CyclicBarrier(2);
        ExecutorService threads = Executors.newFixedThreadPool(2);

        try {
            Future<Optional<Class<?>>> one = threads.submit(start(bothReady, student, first));
            Future<Optional<Class<?>>> other = threads.submit(start(bothReady, student, second));

            List<Optional<Class<?>>> outcomes =
                    List.of(one.get(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                            other.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));

            assertThat(outcomes.stream().filter(Optional::isEmpty).count())
                    .as("exactly one of the two starts commits")
                    .isEqualTo(1);
            assertThat(outcomes.stream().filter(Optional::isPresent).map(Optional::get))
                    .as("and the other is refused for the reason we expect, not by chance")
                    .containsExactly(SessionAlreadyOpenException.class);
        } finally {
            threads.shutdownNow();
        }

        assertThat(sessionCount(student.id()))
                .as("two concurrent sessions would corrupt the duration record, which is the "
                        + "most basic evidence this module holds")
                .isEqualTo(1);
    }

    /**
     * One attempt to start a session, on a thread of its own.
     *
     * @return empty when it started, or the type of the refusal when it did not
     */
    private Callable<Optional<Class<?>>> start(CyclicBarrier bothReady, Account student,
            TopicView topic) {

        return () -> {
            bothReady.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            try {
                sessions.start(student.id(), topic.id(), null, SessionKind.STUDY, 50);
                return Optional.empty();
            } catch (RuntimeException refusal) {
                return Optional.of(refusal.getClass());
            }
        };
    }

    private StudySessionView complete(Account student, TopicView topic, UUID plannedSessionId) {
        StudySessionView started = sessions.start(student.id(), topic.id(), plannedSessionId,
                SessionKind.STUDY, 50);
        return sessions.complete(student.id(), started.id(), RecallRating.GOOD, 30);
    }

    /** Everything the row holds except what identifies it and what is allowed to differ. */
    private String rowOf(UUID sessionId) {
        return jdbc.queryForObject("""
                select kind || '|' || status || '|' || planned_duration_minutes || '|'
                       || actual_duration_minutes || '|' || duration_source || '|' || recall_rating
                  from study_session
                 where id = ?
                """, String.class, sessionId);
    }
}
