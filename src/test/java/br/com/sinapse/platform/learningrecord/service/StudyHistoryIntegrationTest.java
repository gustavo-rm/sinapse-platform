package br.com.sinapse.platform.learningrecord.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.sinapse.platform.curriculum.api.SubjectView;
import br.com.sinapse.platform.curriculum.api.TopicView;
import br.com.sinapse.platform.identity.internal.domain.Account;
import br.com.sinapse.platform.learningrecord.api.AdherenceReport;
import br.com.sinapse.platform.learningrecord.api.RecallRating;
import br.com.sinapse.platform.learningrecord.api.SessionKind;
import br.com.sinapse.platform.learningrecord.api.StudySessionView;
import br.com.sinapse.platform.learningrecord.api.TopicEffort;
import br.com.sinapse.platform.learningrecord.api.TopicRecallTrajectory;
import br.com.sinapse.platform.learningrecord.support.LearningRecordIntegrationTest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The history reads other modules compose with. */
class StudyHistoryIntegrationTest extends LearningRecordIntegrationTest {

    @Test
    void theWindowIsHalfOpenOnTheInstantTheSessionStarted() {
        Account student = student();
        TopicView topic = topic();
        recordedSession(student, topic.id(), 120, 40, RecallRating.GOOD);
        Instant wide = clock.instant().minus(Duration.ofDays(1));
        Instant startedAt = only(history.sessionsOf(student.id(), wide, clock.instant()))
                .startedAt();

        assertThat(history.sessionsOf(student.id(), startedAt, startedAt.plusMillis(1)))
                .as("from is inclusive")
                .hasSize(1);
        assertThat(history.sessionsOf(student.id(), startedAt.plusMillis(1), clock.instant()))
                .isEmpty();
        assertThat(history.sessionsOf(student.id(), wide, startedAt))
                .as("to is exclusive, so consecutive windows tile without counting a session "
                        + "twice — which matters the moment anything sums two of them")
                .isEmpty();
    }

    @Test
    void theHistoryIsMostRecentFirstAndBelongsToOneAccount() {
        Account student = student();
        Account other = student();
        TopicView topic = topic();
        UUID older = recordedSession(student, topic.id(), 300, 30, RecallRating.HARD);
        UUID newer = recordedSession(student, topic.id(), 120, 30, RecallRating.GOOD);
        recordedSession(other, topic.id(), 200, 30, RecallRating.EASY);

        List<StudySessionView> history = window(student);

        assertThat(history).extracting(StudySessionView::id).containsExactly(newer, older);
    }

    @Test
    void theOpenSessionIsTheOneStillRunning() {
        Account student = student();
        TopicView topic = topic();
        recordedSession(student, topic.id(), 200, 30, RecallRating.GOOD);

        assertThat(history.openSessionOf(student.id()))
                .as("a closed session is not an open one")
                .isEmpty();

        StudySessionView started = sessions.start(student.id(), topic.id(), null,
                SessionKind.STUDY, 50);

        assertThat(history.openSessionOf(student.id()))
                .map(StudySessionView::id)
                .contains(started.id());

        sessions.abandon(student.id(), started.id());

        assertThat(history.openSessionOf(student.id())).isEmpty();
    }

    @Test
    void theRecallTrajectoryIsGroupedByTopicAndChronological() {
        Account student = student();
        SubjectView subject = subject();
        TopicView first = topicOf(subject);
        TopicView second = topicOf(subject);
        recordedSession(student, first.id(), 400, 30, RecallRating.AGAIN);
        recordedSession(student, first.id(), 200, 30, RecallRating.HARD);
        recordedSession(student, first.id(), 100, 30, RecallRating.GOOD);
        recordedSession(student, second.id(), 150, 30, RecallRating.EASY);

        List<TopicRecallTrajectory> trajectories =
                history.recallTrajectoriesOf(student.id(), from(), clock.instant());

        assertThat(trajectories).hasSize(2);
        assertThat(trajectoryOf(trajectories, first.id()))
                .as("the sequence is the point: a single rating would be a mood, and ADR 0008 "
                        + "accepted self-report because a trajectory is usable evidence")
                .containsExactly(RecallRating.AGAIN, RecallRating.HARD, RecallRating.GOOD);
        assertThat(trajectoryOf(trajectories, second.id())).containsExactly(RecallRating.EASY);
    }

    @Test
    void anAbandonedSessionContributesNoRatingAndNoTime() {
        Account student = student();
        TopicView topic = topic();
        StudySessionView started = sessions.start(student.id(), topic.id(), null,
                SessionKind.STUDY, 50);
        sessions.abandon(student.id(), started.id());

        assertThat(history.recallTrajectoriesOf(student.id(), from(), clock.instant())).isEmpty();
        assertThat(history.effortByTopicOf(student.id(), from(), clock.instant()))
                .as("the elapsed time of a session somebody walked away from measures nothing")
                .isEmpty();
        assertThat(window(student))
                .as("it is still on the record: what it says is that the session was opened and "
                        + "not finished, which is exactly what adherence needs to know")
                .hasSize(1);
    }

    @Test
    void effortIsSummedPerTopicAndOrderedByHowMuchOfIt() {
        Account student = student();
        SubjectView subject = subject();
        TopicView small = topicOf(subject);
        TopicView large = topicOf(subject);
        recordedSession(student, small.id(), 300, 20, RecallRating.GOOD);
        recordedSession(student, large.id(), 250, 45, RecallRating.GOOD);
        recordedSession(student, large.id(), 200, 45, RecallRating.HARD);

        List<TopicEffort> effort = history.effortByTopicOf(student.id(), from(), clock.instant());

        assertThat(effort).extracting(TopicEffort::topicId).containsExactly(large.id(), small.id());
        assertThat(effort.get(0).effort()).isEqualTo(Duration.ofMinutes(90));
        assertThat(effort.get(0).sessionCount())
                .as("ninety minutes over two sessions and ninety over one are different "
                        + "evidence, and the total alone cannot tell them apart")
                .isEqualTo(2);
        assertThat(effort.get(1).effort()).isEqualTo(Duration.ofMinutes(20));
    }

    @Test
    void effortOfSeveralAccountsIsOneQuery() {
        Account first = student();
        Account second = student();
        Account idle = student();
        TopicView topic = topic();
        recordedSession(first, topic.id(), 300, 20, RecallRating.GOOD);
        recordedSession(first, topic.id(), 200, 25, RecallRating.GOOD);
        recordedSession(second, topic.id(), 200, 60, RecallRating.HARD);

        Map<UUID, Duration> effort = history.effortOf(
                List.of(first.id(), second.id(), idle.id()), from(), clock.instant());

        assertThat(effort).containsOnlyKeys(first.id(), second.id());
        assertThat(effort.get(first.id())).isEqualTo(Duration.ofMinutes(45));
        assertThat(effort.get(second.id())).isEqualTo(Duration.ofMinutes(60));
        assertThat(history.effortOf(List.of(), from(), clock.instant()))
                .as("an empty classroom is a real case, and asking the database about nothing "
                        + "is a query that can only return nothing")
                .isEmpty();
    }

    @Test
    void anExecutionIsFoundByThePlannedSessionItCameFrom() {
        Account student = student();
        TopicView topic = topic();
        UUID planned = UUID.randomUUID();
        UUID otherPlanned = UUID.randomUUID();
        UUID abandoned = insertAbandonedAttempt(student.id(), topic.id(), planned, 90);
        StudySessionView started = sessions.start(student.id(), topic.id(), planned,
                SessionKind.STUDY, 50);
        StudySessionView completed = sessions.complete(student.id(), started.id(),
                RecallRating.GOOD, 40);

        Map<UUID, StudySessionView> executions =
                history.executionsOf(List.of(planned, otherPlanned));

        assertThat(executions).containsOnlyKeys(planned);
        assertThat(executions.get(planned).id())
                .as("the agenda shows the attempt that stands against a scheduled slot; the "
                        + "abandoned one before it is in the history, which is where it belongs")
                .isEqualTo(completed.id());
        assertThat(window(student)).extracting(StudySessionView::id)
                .contains(abandoned, completed.id());
        assertThat(history.executionsOf(List.of())).isEmpty();
    }

    @Test
    void adherenceCountsCompletedPlannedSessionsAndNothingElse() {
        Account student = student();
        TopicView topic = topic();
        UUID followed = UUID.randomUUID();
        UUID givenUp = UUID.randomUUID();
        UUID untouched = UUID.randomUUID();

        StudySessionView first = sessions.start(student.id(), topic.id(), followed,
                SessionKind.STUDY, 50);
        sessions.complete(student.id(), first.id(), RecallRating.GOOD, 40);
        StudySessionView second = sessions.start(student.id(), topic.id(), givenUp,
                SessionKind.STUDY, 50);
        sessions.abandon(student.id(), second.id());

        AdherenceReport report = history.adherenceOf(student.id(),
                List.of(followed, givenUp, untouched));

        assertThat(report.planned()).isEqualTo(3);
        assertThat(report.executed())
                .as("a session the student opened and gave up on is not adherence; counting it "
                        + "would make the measure agree with itself rather than with what happened")
                .isEqualTo(1);
        assertThat(report.notExecuted()).isEqualTo(2);
        assertThat(report.ratio()).hasValue(1.0 / 3);
    }

    @Test
    void adherenceIsAskedOnlyAboutTheSessionsTheCallerNames() {
        Account student = student();
        TopicView topic = topic();
        UUID planned = UUID.randomUUID();
        StudySessionView started = sessions.start(student.id(), topic.id(), planned,
                SessionKind.STUDY, 50);
        sessions.complete(student.id(), started.id(), RecallRating.GOOD, 40);
        recordedSession(student, topic.id(), 200, 30, RecallRating.GOOD);

        assertThat(history.adherenceOf(student.id(), List.of()).ratio())
                .as("nothing planned has no ratio to report, and this module cannot invent one: "
                        + "which sessions are due is planning's to decide, by rule R2")
                .isEmpty();
        assertThat(history.adherenceOf(student.id(), List.of(planned, planned)).planned())
                .as("asking about the same planned session twice must not move the denominator")
                .isEqualTo(1);
        assertThat(history.adherenceOf(student.id(), List.of(planned)).executed())
                .as("the self-directed session is not counted against a plan it was never part of")
                .isEqualTo(1);
    }

    /**
     * An attempt that started at a chosen instant, which the services cannot produce.
     *
     * <p>A timed session starts now and a retroactive one is always completed, so the one
     * state a test cannot reach through them is an abandoned attempt in the past. Writing the
     * row is how the state the rule is about gets to exist.
     */
    private UUID insertAbandonedAttempt(UUID accountId, UUID topicId, UUID plannedSessionId,
            long minutesAgo) {

        UUID id = UUID.randomUUID();
        Instant startedAt = clock.instant().minus(Duration.ofMinutes(minutesAgo));
        jdbc.update(INSERT_SESSION, id, accountId, topicId, plannedSessionId, "STUDY", "FROM_PLAN",
                "ABANDONED", Timestamp.from(startedAt),
                Timestamp.from(startedAt.plus(Duration.ofMinutes(5))), 50, null, null, null);
        return id;
    }

    private List<StudySessionView> window(Account student) {
        return history.sessionsOf(student.id(), from(), clock.instant().plusSeconds(1));
    }

    private Instant from() {
        return clock.instant().minus(Duration.ofDays(2));
    }

    private static StudySessionView only(List<StudySessionView> sessions) {
        assertThat(sessions).hasSize(1);
        return sessions.get(0);
    }

    private static List<RecallRating> trajectoryOf(List<TopicRecallTrajectory> trajectories,
            UUID topicId) {

        return trajectories.stream()
                .filter(trajectory -> trajectory.topicId().equals(topicId))
                .flatMap(trajectory -> trajectory.points().stream())
                .map(TopicRecallTrajectory.Point::rating)
                .toList();
    }
}
