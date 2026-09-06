package br.com.sinapse.platform.learningrecord.internal.service;

import br.com.sinapse.platform.learningrecord.api.AdherenceReport;
import br.com.sinapse.platform.learningrecord.api.LearningRecordConsistency;
import br.com.sinapse.platform.learningrecord.api.SessionStatus;
import br.com.sinapse.platform.learningrecord.api.StudyHistory;
import br.com.sinapse.platform.learningrecord.api.StudySessionView;
import br.com.sinapse.platform.learningrecord.api.TopicEffort;
import br.com.sinapse.platform.learningrecord.api.TopicRecallTrajectory;
import br.com.sinapse.platform.learningrecord.internal.domain.StudySession;
import br.com.sinapse.platform.learningrecord.internal.persistence.AccountEffortRow;
import br.com.sinapse.platform.learningrecord.internal.persistence.StudySessionRepository;
import br.com.sinapse.platform.learningrecord.internal.persistence.TopicEffortRow;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The history reads this module publishes.
 *
 * <p>Read-only throughout, and every method hands back a DTO. Whether the caller may see the
 * student is settled before this is reached — see {@link StudyHistory} — so nothing here
 * filters by who is asking.
 *
 * <p>An empty set of identifiers is answered without going to the database. It is a real
 * case: a day with no planned sessions, a classroom with no students. Sending it as an
 * {@code in ()} would be a query that can only return nothing.
 */
@Service
@Transactional(readOnly = true)
public class StudyHistoryService implements StudyHistory, LearningRecordConsistency {

    private final StudySessionRepository sessions;

    /**
     * @param sessions study sessions
     */
    public StudyHistoryService(StudySessionRepository sessions) {
        this.sessions = sessions;
    }

    @Override
    public List<StudySessionView> sessionsOf(UUID accountId, Instant from, Instant to) {
        return sessions.findInWindow(accountId, from, to).stream()
                .map(LearningRecordViews::of)
                .toList();
    }

    @Override
    public Optional<StudySessionView> openSessionOf(UUID accountId) {
        return sessions.findByAccountIdAndStatus(accountId, SessionStatus.IN_PROGRESS)
                .map(LearningRecordViews::of);
    }

    @Override
    public List<TopicRecallTrajectory> recallTrajectoriesOf(UUID accountId, Instant from,
            Instant to) {

        // Ordered by topic and then chronologically, so one pass produces the trajectories
        // already in the order they have to be read in.
        Map<UUID, List<TopicRecallTrajectory.Point>> byTopic = new LinkedHashMap<>();
        for (StudySession session : sessions.findRatedInWindow(accountId, from, to)) {
            byTopic.computeIfAbsent(session.topicId(), topic -> new ArrayList<>())
                    .add(new TopicRecallTrajectory.Point(session.endedAt(), session.recallRating()));
        }
        return byTopic.entrySet().stream()
                .map(entry -> new TopicRecallTrajectory(entry.getKey(), entry.getValue()))
                .toList();
    }

    @Override
    public List<TopicEffort> effortByTopicOf(UUID accountId, Instant from, Instant to) {
        return sessions.sumEffortByTopic(accountId, from, to).stream()
                .map(StudyHistoryService::effortOf)
                .toList();
    }

    @Override
    public Map<UUID, Duration> effortOf(Collection<UUID> accountIds, Instant from, Instant to) {
        if (accountIds.isEmpty()) {
            return Map.of();
        }
        return sessions.sumEffortByAccount(accountIds, from, to).stream()
                .collect(Collectors.toMap(AccountEffortRow::accountId,
                        row -> Duration.ofMinutes(row.minutes())));
    }

    @Override
    public Map<UUID, StudySessionView> executionsOf(Collection<UUID> plannedSessionIds) {
        if (plannedSessionIds.isEmpty()) {
            return Map.of();
        }
        // Oldest first, and the merge keeps the later one: an attempt that was abandoned and
        // then picked up again shows as the attempt that stands.
        return sessions.findByPlannedSessions(plannedSessionIds).stream()
                .collect(Collectors.toMap(StudySession::plannedSessionId,
                        LearningRecordViews::of,
                        (earlier, later) -> later,
                        LinkedHashMap::new));
    }

    @Override
    public AdherenceReport adherenceOf(UUID accountId, Collection<UUID> duePlannedSessionIds) {
        if (duePlannedSessionIds.isEmpty()) {
            return new AdherenceReport(0, 0);
        }
        // Distinct on the caller's side too: asking about the same planned session twice must
        // not make the denominator disagree with what the query counted.
        int planned = Math.toIntExact(duePlannedSessionIds.stream().distinct().count());
        long executed = sessions.countCompletedPlannedSessions(accountId, duePlannedSessionIds);
        return new AdherenceReport(planned, Math.toIntExact(executed));
    }

    @Override
    public List<UUID> referencedPlannedSessionIds() {
        return sessions.findReferencedPlannedSessionIds();
    }

    private static TopicEffort effortOf(TopicEffortRow row) {
        return new TopicEffort(row.topicId(), Duration.ofMinutes(row.minutes()),
                Math.toIntExact(row.sessionCount()));
    }
}
