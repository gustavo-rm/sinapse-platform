package br.com.sinapse.platform.planning.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads of what has been planned, for whatever composes above this module.
 *
 * <p>Every method returns a DTO. A {@code StudyPlan} handed across the boundary would be an
 * immutable aggregate with a JPA identity attached, and the first caller to touch a field on
 * it would meet a trigger instead of a compiler.
 *
 * <p>Lookups that could be asked about many things take a set, per rule R7 and section 4 of
 * the API contract: the read models compose across modules without joining, and a module that
 * offers only unit lookups turns one screen into forty queries.
 *
 * <p>Nothing here checks authorisation. Whether the caller may read this student is
 * {@code AccountAccessPolicy} or {@code TeacherAccessPolicy}, asked before this is reached — a
 * read that quietly filtered by who is asking would be a second place where the access rule
 * lives.
 */
public interface PlanningDirectory {

    /**
     * The plan currently in force for an account.
     *
     * @param accountId student
     * @return the active plan, if there is one
     */
    Optional<StudyPlanView> activePlanOf(UUID accountId);

    /**
     * Every plan an account has had, newest first.
     *
     * <p>The supersession chain is readable from the result: each superseded plan carries the
     * identifier of the one that replaced it. Returned whole rather than paged — a student
     * re-plans by hand, on their own initiative (decision F6), so this list grows in single
     * figures and a hard server-side cap is enough (section 1 of the API contract).
     *
     * @param accountId student
     * @return the plans, most recent first
     */
    List<StudyPlanView> planHistoryOf(UUID accountId);

    /**
     * A plan by identifier.
     *
     * @param planId plan
     * @return the plan, if it exists
     */
    Optional<StudyPlanView> plan(UUID planId);

    /**
     * The sessions of the account's <em>active</em> plan that start within a window.
     *
     * <p>This is what the day's agenda reads, so it answers about the plan in force and not
     * about every plan the student has ever had. The sessions of a plan that was replaced are
     * still there and are read through {@link #sessionsOfPlan}, which is how a summary of that
     * plan gets at them.
     *
     * @param accountId student
     * @param from      start of the window, inclusive
     * @param to        end of the window, exclusive
     * @return the sessions, earliest first
     */
    List<PlannedSessionView> plannedSessionsOf(UUID accountId, Instant from, Instant to);

    /**
     * Every session of one plan, in the order the core sequenced them.
     *
     * @param planId plan
     * @return its sessions
     */
    List<PlannedSessionView> sessionsOfPlan(UUID planId);

    /**
     * Planned sessions by identifier.
     *
     * <p>The batch lookup section 4 of the contract asks for: an executed session carries a
     * planned session identifier and no foreign key, so anything joining the two does it here,
     * for a whole screen at once.
     *
     * @param plannedSessionIds sessions to look up
     * @return the ones that exist, keyed by identifier
     */
    Map<UUID, PlannedSessionView> plannedSessionsByIds(Collection<UUID> plannedSessionIds);

    /**
     * The availability windows of an account that were in force on a given day.
     *
     * <p>The day comes from the caller. Weekly windows are local times in the account's own
     * zone, so "today" is a question about that zone rather than about the server's, and the
     * caller — a client that knows its own date, or the snapshot assembly that knows the
     * horizon — is who can answer it.
     *
     * @param accountId student
     * @param date      day being asked about
     * @return the windows in force, ordered by day and then by start time
     */
    List<AvailabilityWindowView> availabilityOn(UUID accountId, LocalDate date);

    /**
     * Every availability window an account has declared, closed ones included.
     *
     * @param accountId student
     * @return the windows, ordered by day and then by start time
     */
    List<AvailabilityWindowView> availabilityOf(UUID accountId);

    /**
     * The goals an account is currently working towards.
     *
     * @param accountId student
     * @return the active goals, most pressing first
     */
    List<StudyGoalView> activeGoalsOf(UUID accountId);

    /**
     * Every goal an account has set, closed ones included.
     *
     * @param accountId student
     * @return the goals, most pressing first, then newest first
     */
    List<StudyGoalView> goalsOf(UUID accountId);
}
