package br.com.sinapse.platform.learningrecord.internal.persistence;

import br.com.sinapse.platform.learningrecord.api.SessionStatus;
import br.com.sinapse.platform.learningrecord.internal.domain.StudySession;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Study sessions.
 *
 * <p>Nothing here bulk-updates, and the one delete is the Article 18 erasure. A trigger
 * refuses a delete on any row unless the transaction-local erasure flag is set, so
 * {@link #eraseFor} is not a hole in the append-only rule — it is the one path that carries
 * the exception with it, and any other caller meets the trigger.
 *
 * <p>Every window is half-open: {@code from} inclusive, {@code to} exclusive, compared
 * against {@code startedAt}. Consecutive windows therefore tile without a session falling
 * into both, which matters as soon as anything sums two of them.
 */
public interface StudySessionRepository extends JpaRepository<StudySession, UUID> {

    /**
     * The session an account has in this state.
     *
     * <p>Asked with {@link SessionStatus#IN_PROGRESS}, where the partial unique index
     * guarantees there is at most one.
     *
     * @param accountId student
     * @param status    state being looked for
     * @return the session, if there is one
     */
    Optional<StudySession> findByAccountIdAndStatus(UUID accountId, SessionStatus status);

    /**
     * The sessions of an account that started within a window, most recent first.
     *
     * @param accountId student
     * @param from      start of the window, inclusive
     * @param to        end of the window, exclusive
     * @return the sessions, running ones included
     */
    @Query("""
            select session
              from StudySession session
             where session.accountId = :accountId
               and session.startedAt >= :from
               and session.startedAt < :to
             order by session.startedAt desc
            """)
    List<StudySession> findInWindow(@Param("accountId") UUID accountId,
            @Param("from") Instant from, @Param("to") Instant to);

    /**
     * The rated sessions of an account within a window, grouped by topic and oldest first.
     *
     * <p>A rating exists only on a completed session, by invariant 4, so the filter on the
     * rating being present is what selects them. The ordering is the trajectory: it has to be
     * chronological within each topic, and doing it here rather than in memory is what lets
     * the caller group in one pass.
     *
     * @param accountId student
     * @param from      start of the window, inclusive
     * @param to        end of the window, exclusive
     * @return the rated sessions, ordered by topic and then by the instant they closed
     */
    @Query("""
            select session
              from StudySession session
             where session.accountId = :accountId
               and session.recallRating is not null
               and session.startedAt >= :from
               and session.startedAt < :to
             order by session.topicId, session.endedAt
            """)
    List<StudySession> findRatedInWindow(@Param("accountId") UUID accountId,
            @Param("from") Instant from, @Param("to") Instant to);

    /**
     * Effective time per topic for an account within a window, longest first.
     *
     * <p>Only sessions that have a duration contribute, which is the same set as the sessions
     * that were completed: an abandoned session is deliberately left without one.
     *
     * @param accountId student
     * @param from      start of the window, inclusive
     * @param to        end of the window, exclusive
     * @return one row per topic with recorded time
     */
    @Query("""
            select new br.com.sinapse.platform.learningrecord.internal.persistence.TopicEffortRow(
                       session.topicId, sum(session.actualDurationMinutes), count(session))
              from StudySession session
             where session.accountId = :accountId
               and session.actualDurationMinutes is not null
               and session.startedAt >= :from
               and session.startedAt < :to
             group by session.topicId
             order by sum(session.actualDurationMinutes) desc
            """)
    List<TopicEffortRow> sumEffortByTopic(@Param("accountId") UUID accountId,
            @Param("from") Instant from, @Param("to") Instant to);

    /**
     * Effective time for each of several accounts within a window, in one query.
     *
     * <p>Rule R7. This is what a teacher's class list reads, and one query per student would
     * make the heaviest screen in the system heavier by a factor of the class size.
     *
     * @param accountIds students
     * @param from       start of the window, inclusive
     * @param to         end of the window, exclusive
     * @return one row per account that has recorded time
     */
    @Query("""
            select new br.com.sinapse.platform.learningrecord.internal.persistence.AccountEffortRow(
                       session.accountId, sum(session.actualDurationMinutes))
              from StudySession session
             where session.accountId in :accountIds
               and session.actualDurationMinutes is not null
               and session.startedAt >= :from
               and session.startedAt < :to
             group by session.accountId
            """)
    List<AccountEffortRow> sumEffortByAccount(@Param("accountIds") Collection<UUID> accountIds,
            @Param("from") Instant from, @Param("to") Instant to);

    /**
     * The sessions that executed any of a set of planned sessions, oldest first.
     *
     * <p>Ordered so that a caller building a map of one entry per planned session keeps the
     * last one it sees, which is the most recent attempt.
     *
     * @param plannedSessionIds planned sessions to look up
     * @return the sessions referencing them
     */
    @Query("""
            select session
              from StudySession session
             where session.plannedSessionId in :plannedSessionIds
             order by session.startedAt
            """)
    List<StudySession> findByPlannedSessions(
            @Param("plannedSessionIds") Collection<UUID> plannedSessionIds);

    /**
     * How many of a set of planned sessions the account completed.
     *
     * <p>Distinct, because a planned session attempted twice and completed twice is one
     * planned session that was followed. Counting attempts would let a student score above
     * their own plan.
     *
     * @param accountId         student
     * @param plannedSessionIds planned sessions being asked about
     * @return how many of them have a completed execution
     */
    @Query("""
            select count(distinct session.plannedSessionId)
              from StudySession session
             where session.accountId = :accountId
               and session.plannedSessionId in :plannedSessionIds
               and session.status = br.com.sinapse.platform.learningrecord.api.SessionStatus.COMPLETED
            """)
    long countCompletedPlannedSessions(@Param("accountId") UUID accountId,
            @Param("plannedSessionIds") Collection<UUID> plannedSessionIds);

    /**
     * Every session of an account, newest first.
     *
     * <p>Unwindowed, unlike every other read here, because it answers the holder asking for
     * their own data: an export bounded by a window would not be the export Article 18 means.
     *
     * @param accountId student
     * @return their whole history
     */
    List<StudySession> findByAccountIdOrderByStartedAtDesc(UUID accountId);

    /**
     * Removes every session of an account.
     *
     * <p>Only ever called from the erasure transaction. The trigger refuses this statement
     * unless the transaction-local flag is set, so a caller that reaches it by another route
     * fails rather than succeeding quietly.
     *
     * <p>A longitudinal sequence of timestamped topics is a behavioural fingerprint and
     * re-identifies when crossed with a classroom roster, so there is no "scrub and keep" path
     * here and there must never be one (ADR 0011).
     *
     * @param accountId student whose data is being erased
     * @return how many rows were removed
     */
    @Modifying
    @Query("delete from StudySession session where session.accountId = :accountId")
    int eraseFor(@Param("accountId") UUID accountId);

    /**
     * Every planned session identifier this module holds a reference to.
     *
     * <p>Read by the consistency job that looks for references to planned sessions that no
     * longer exist. Distinct, because the job asks about identifiers rather than about
     * sessions, and a plan followed twice would otherwise be checked twice.
     *
     * @return the identifiers, in no particular order
     */
    @Query("""
            select distinct session.plannedSessionId
              from StudySession session
             where session.plannedSessionId is not null
            """)
    List<UUID> findReferencedPlannedSessionIds();
}
