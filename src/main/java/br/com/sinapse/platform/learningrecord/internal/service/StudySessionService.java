package br.com.sinapse.platform.learningrecord.internal.service;

import br.com.sinapse.platform.curriculum.api.CurriculumCatalog;
import br.com.sinapse.platform.learningrecord.api.RecallRating;
import br.com.sinapse.platform.learningrecord.api.SessionKind;
import br.com.sinapse.platform.learningrecord.api.SessionStatus;
import br.com.sinapse.platform.learningrecord.api.StudySessionView;
import br.com.sinapse.platform.learningrecord.internal.config.LearningRecordProperties;
import br.com.sinapse.platform.learningrecord.internal.domain.StudySession;
import br.com.sinapse.platform.learningrecord.internal.error.InvalidTimeWindowException;
import br.com.sinapse.platform.learningrecord.internal.error.SessionAlreadyOpenException;
import br.com.sinapse.platform.learningrecord.internal.error.UnknownSessionException;
import br.com.sinapse.platform.learningrecord.internal.error.UnknownTopicException;
import br.com.sinapse.platform.learningrecord.internal.persistence.StudySessionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The session lifecycle: starting one, closing it, and recording one that already happened.
 *
 * <p><strong>One path, two sources.</strong> A session taken from the plan and a session the
 * student decided on go through the same method and produce the same row; the only difference
 * is whether a planned session identifier was supplied. There is no branch here that a
 * self-directed session takes and a planned one does not, which is what section 8.1 asks for.
 *
 * <p>{@code plannedDurationMinutes} comes from the caller rather than from the plan. This
 * module may not read planning (rule R2), so it cannot look the number up; the client already
 * holds it, because it read the agenda in order to know there was a session to start. The
 * cost is that the value is copied rather than derived, and the alternative was a dependency
 * the architecture rules out.
 *
 * <p>Invariant 1 is checked twice, and not out of nervousness. The lookup gives a caller a
 * 409 that names the situation; the partial unique index is what actually holds under two
 * simultaneous requests, and the caught constraint violation is how that reaches the client
 * as the same 409 rather than as a 500.
 */
@Service
public class StudySessionService {

    private final StudySessionRepository sessions;
    private final CurriculumCatalog catalog;
    private final LearningRecordAccess access;
    private final Duration maxBackdating;
    private final Clock clock;

    /**
     * @param sessions   study sessions
     * @param catalog    the curriculum catalogue, consulted so that an unknown topic is a 404
     *                   rather than a foreign key violation
     * @param access     the access gate of this module
     * @param properties configured limits
     * @param clock      application clock
     */
    public StudySessionService(StudySessionRepository sessions, CurriculumCatalog catalog,
            LearningRecordAccess access, LearningRecordProperties properties, Clock clock) {
        this.sessions = sessions;
        this.catalog = catalog;
        this.access = access;
        this.maxBackdating = properties.maxRetroactiveBackdating();
        this.clock = clock;
    }

    /**
     * Starts a session and lets the application time it.
     *
     * @param accountId              student
     * @param topicId                topic being studied
     * @param plannedSessionId       planned session being executed, or {@code null} when the
     *                               student is studying on their own initiative
     * @param kind                   new ground or going back over it
     * @param plannedDurationMinutes what the plan asked for, or {@code null}
     * @return the running session
     * @throws SessionAlreadyOpenException if the account already has one running
     * @throws UnknownTopicException       if the topic is not in the catalogue
     */
    @Transactional
    public StudySessionView start(UUID accountId, UUID topicId, UUID plannedSessionId,
            SessionKind kind, Integer plannedDurationMinutes) {

        access.requireProcessable(accountId);
        requireKnownTopic(topicId);
        requireNothingOpen(accountId);

        StudySession session = StudySession.start(UUID.randomUUID(), accountId, topicId,
                plannedSessionId, kind, clock.instant(), plannedDurationMinutes);
        return LearningRecordViews.of(persist(session));
    }

    /**
     * Closes a running session as completed.
     *
     * <p>With no duration supplied the application's own measurement is used, which is the
     * main path of decision F5. A duration supplied here is the student correcting a timer
     * that did not reflect what happened, and the record says so: it is stored as
     * self-reported, and the two sets stay separable.
     *
     * @param accountId             student
     * @param sessionId             session to close
     * @param recallRating          the student's judgement of their recall
     * @param actualDurationMinutes duration the student states, or {@code null} to use the
     *                              measured one
     * @return the closed session
     * @throws UnknownSessionException if there is no such session for this account
     */
    @Transactional
    public StudySessionView complete(UUID accountId, UUID sessionId, RecallRating recallRating,
            Integer actualDurationMinutes) {

        access.requireProcessable(accountId);
        StudySession session = requireOwned(accountId, sessionId);
        Instant now = clock.instant();

        if (actualDurationMinutes == null) {
            session.complete(now, recallRating);
        } else {
            session.completeWithReportedDuration(now, actualDurationMinutes, recallRating);
        }
        return LearningRecordViews.of(session);
    }

    /**
     * Closes a running session as abandoned.
     *
     * @param accountId student
     * @param sessionId session to close
     * @return the closed session
     * @throws UnknownSessionException if there is no such session for this account
     */
    @Transactional
    public StudySessionView abandon(UUID accountId, UUID sessionId) {
        access.requireProcessable(accountId);
        StudySession session = requireOwned(accountId, sessionId);
        session.abandon(clock.instant());
        return LearningRecordViews.of(session);
    }

    /**
     * Records a session that already happened and was never timed.
     *
     * <p>The marked exception of decision F5. It is born closed, so it does not occupy the
     * one running slot an account has: a student can enter yesterday's session while today's
     * is running, and neither record is disturbed by the other.
     *
     * <p>How far back it may reach is configuration. Without a bound, this route is a way of
     * writing a study history that was never studied, and the whole point of marking the
     * duration as self-reported is that a self-reported history is still worth something —
     * which stops being true once it can be composed after the fact at any length.
     *
     * @param accountId             student
     * @param topicId               topic studied
     * @param plannedSessionId      planned session executed, or {@code null}
     * @param kind                  new ground or going back over it
     * @param startedAt             instant the student says it started
     * @param actualDurationMinutes how long the student says it took
     * @param recallRating          the student's judgement of their recall
     * @return the recorded session
     * @throws InvalidTimeWindowException if the session is in the future or older than the
     *                                    configured limit
     * @throws UnknownTopicException      if the topic is not in the catalogue
     */
    @Transactional
    public StudySessionView recordRetroactively(UUID accountId, UUID topicId,
            UUID plannedSessionId, SessionKind kind, Instant startedAt, int actualDurationMinutes,
            RecallRating recallRating) {

        access.requireProcessable(accountId);
        requireKnownTopic(topicId);

        Instant now = clock.instant();
        Instant endedAt = startedAt.plus(Duration.ofMinutes(actualDurationMinutes));
        if (startedAt.isBefore(now.minus(maxBackdating)) || endedAt.isAfter(now)) {
            throw new InvalidTimeWindowException();
        }

        StudySession session = StudySession.recordRetroactively(UUID.randomUUID(), accountId,
                topicId, plannedSessionId, kind, startedAt, actualDurationMinutes, recallRating);
        return LearningRecordViews.of(persist(session));
    }

    private StudySession persist(StudySession session) {
        try {
            // Flushed here so that the partial unique index answers inside this call. Left to
            // the end of the transaction, the violation would surface from the commit, where
            // there is no longer a handler that can turn it into a 409.
            return sessions.saveAndFlush(session);
        } catch (DataIntegrityViolationException violation) {
            // The only unique constraint this table has is the one in-progress session per
            // account. The check above lost a race with another request.
            throw new SessionAlreadyOpenException();
        }
    }

    private StudySession requireOwned(UUID accountId, UUID sessionId) {
        return sessions.findById(sessionId)
                .filter(session -> session.accountId().equals(accountId))
                .orElseThrow(UnknownSessionException::new);
    }

    private void requireNothingOpen(UUID accountId) {
        sessions.findByAccountIdAndStatus(accountId, SessionStatus.IN_PROGRESS)
                .ifPresent(open -> {
                    throw new SessionAlreadyOpenException();
                });
    }

    private void requireKnownTopic(UUID topicId) {
        List<?> found = catalog.topicsByIds(List.of(topicId));
        if (found.isEmpty()) {
            throw new UnknownTopicException();
        }
    }
}
