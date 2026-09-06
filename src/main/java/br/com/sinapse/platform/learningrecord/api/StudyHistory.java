package br.com.sinapse.platform.learningrecord.api;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads of executed study sessions, for whatever composes above this module.
 *
 * <p>Every method returns a DTO. Nothing here hands out an entity, because an entity handed
 * across a module boundary is a JPA association waiting to be written and a closed session
 * waiting to be edited.
 *
 * <p><strong>Windows are mandatory on anything that grows.</strong> History has no natural
 * size, so section 1 of the API contract requires {@code from} and {@code to} with a
 * configured maximum span rather than a page mechanism nobody has a case for yet. The span is
 * checked at the edge, where the request arrives; the methods here take whatever window the
 * caller asks for.
 *
 * <p><strong>Batch lookups, per rule R7 and section 4 of the contract.</strong> The read
 * models compose across modules without joining, so a module offering only unit lookups turns
 * one screen into forty queries. Two methods here exist for that reason and no other:
 * {@link #executionsOf} answers the day's agenda for any number of planned sessions at once,
 * and {@link #effortOf} answers a class list for any number of students at once.
 *
 * <p>Adherence for many students at once is built from {@link #executionsOf} rather than from
 * a batch of its own: the caller passes every student's due planned sessions in one set and
 * counts the answers per student. The one-account {@link #adherenceOf} is a convenience over
 * the same query, not a second way of asking.
 *
 * <p>Nothing here checks authorisation. Whether the caller may read a student's data is
 * {@code AccountAccessPolicy} or {@code TeacherAccessPolicy}, asked before this is reached —
 * a read that quietly filtered by what the caller may see would be a second place where the
 * access rule lives.
 */
public interface StudyHistory {

    /**
     * The sessions of an account within a window, most recent first.
     *
     * @param accountId student
     * @param from      start of the window, inclusive
     * @param to        end of the window, exclusive
     * @return the sessions that started within it, running ones included
     */
    List<StudySessionView> sessionsOf(UUID accountId, Instant from, Instant to);

    /**
     * The session an account currently has open, if any.
     *
     * <p>At most one, by the partial index. The initial screen reads this so that it can
     * offer to resume rather than start a second one and be refused (section 3.1 of the API
     * contract).
     *
     * @param accountId student
     * @return their running session, if there is one
     */
    Optional<StudySessionView> openSessionOf(UUID accountId);

    /**
     * The recall ratings of an account within a window, grouped by topic.
     *
     * <p>Only completed sessions appear: a rating exists nowhere else, by invariant 4.
     *
     * @param accountId student
     * @param from      start of the window, inclusive
     * @param to        end of the window, exclusive
     * @return one trajectory per topic rated in the window, points oldest first
     */
    List<TopicRecallTrajectory> recallTrajectoriesOf(UUID accountId, Instant from, Instant to);

    /**
     * Total effective time per topic for an account within a window.
     *
     * @param accountId student
     * @param from      start of the window, inclusive
     * @param to        end of the window, exclusive
     * @return one entry per topic with recorded time, longest first
     */
    List<TopicEffort> effortByTopicOf(UUID accountId, Instant from, Instant to);

    /**
     * Total effective time for each of several accounts within a window.
     *
     * <p>The class list of a teacher's classroom, in one query rather than one per student.
     *
     * @param accountIds students
     * @param from       start of the window, inclusive
     * @param to         end of the window, exclusive
     * @return time per account; an account with no recorded time is absent from the map
     */
    Map<UUID, Duration> effortOf(Collection<UUID> accountIds, Instant from, Instant to);

    /**
     * The execution of each of several planned sessions.
     *
     * <p>A planned session can have been attempted more than once — abandoned and picked up
     * again — and the entry here is the most recent attempt. That is what the day's agenda
     * shows against a scheduled slot; the attempts that came before are in
     * {@link #sessionsOf}, which is where a history belongs.
     *
     * @param plannedSessionIds planned sessions to look up
     * @return the sessions that executed them, keyed by planned session. A planned session
     *         nobody executed is absent from the map
     */
    Map<UUID, StudySessionView> executionsOf(Collection<UUID> plannedSessionIds);

    /**
     * How many of an account's due planned sessions were completed.
     *
     * @param accountId              student
     * @param duePlannedSessionIds   planned sessions of that student that have already fallen
     *                               due. Deciding which those are belongs to the caller: see
     *                               {@link AdherenceReport}
     * @return the counts, with the ratio derived
     */
    AdherenceReport adherenceOf(UUID accountId, Collection<UUID> duePlannedSessionIds);
}
