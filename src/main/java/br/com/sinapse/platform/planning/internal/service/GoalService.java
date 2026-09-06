package br.com.sinapse.platform.planning.internal.service;

import br.com.sinapse.platform.curriculum.api.CurriculumCatalog;
import br.com.sinapse.platform.curriculum.api.SubjectView;
import br.com.sinapse.platform.planning.api.GoalStatus;
import br.com.sinapse.platform.planning.api.StudyGoalView;
import br.com.sinapse.platform.planning.internal.domain.StudyGoal;
import br.com.sinapse.platform.planning.internal.error.DuplicateGoalException;
import br.com.sinapse.platform.planning.internal.error.UnknownGoalException;
import br.com.sinapse.platform.planning.internal.error.UnknownSubjectException;
import br.com.sinapse.platform.planning.internal.persistence.StudyGoalRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Setting, revising and closing the subjects a student intends to get through.
 *
 * <p>A goal names a subject and only a subject. There is no method that adds or excludes a
 * topic, by decision L3: every topic of the subject is in scope and the core decides the order
 * and what fits the horizon.
 *
 * <p>At most one active goal per subject, which the partial index guarantees. The lookup here
 * gives a caller a 409 that names the situation; the caught constraint violation is what
 * covers two requests arriving at once, where both lookups pass because neither transaction
 * sees the other's uncommitted row.
 */
@Service
public class GoalService {

    private final StudyGoalRepository goals;
    private final CurriculumCatalog catalog;
    private final PlanningAccess access;
    private final Clock clock;

    /**
     * @param goals   goals
     * @param catalog the curriculum catalogue, consulted so that an unknown subject is a 404
     *                rather than a foreign key violation
     * @param access  the access gate of this module
     * @param clock   application clock
     */
    public GoalService(StudyGoalRepository goals, CurriculumCatalog catalog, PlanningAccess access,
            Clock clock) {
        this.goals = goals;
        this.catalog = catalog;
        this.access = access;
        this.clock = clock;
    }

    /**
     * Sets a goal.
     *
     * @param accountId  student
     * @param subjectId  subject in the curriculum catalogue
     * @param targetDate when the student would like to be done, or {@code null}. A
     *                   prioritisation constraint, never the plan horizon
     * @param priority   1 to 5, higher meaning more pressing
     * @return the goal
     * @throws UnknownSubjectException if the subject is not in the catalogue
     * @throws DuplicateGoalException  if the account already has an active goal for it
     */
    @Transactional
    public StudyGoalView set(UUID accountId, UUID subjectId, LocalDate targetDate, int priority) {
        access.requireProcessable(accountId);
        requireKnownSubject(subjectId);
        requireNoActiveGoal(accountId, subjectId);

        StudyGoal goal = new StudyGoal(UUID.randomUUID(), accountId, subjectId, targetDate,
                priority, clock.instant());
        try {
            // Flushed here so that the partial index answers inside this call. Left to the end
            // of the transaction, the violation would surface from the commit, where there is
            // no longer a handler that can turn it into a 409.
            return PlanningViews.of(goals.saveAndFlush(goal));
        } catch (DataIntegrityViolationException violation) {
            // The only unique constraint this table has is one active goal per subject. The
            // lookup above lost a race with another request.
            throw new DuplicateGoalException();
        }
    }

    /**
     * Changes what an active goal asks for.
     *
     * <p>The subject is not among what can change: a goal about another subject is another
     * goal, and repointing one would move the history of an abandoned subject onto a new one.
     *
     * @param accountId  student
     * @param goalId     goal to revise
     * @param targetDate new target date, or {@code null} to drop it
     * @param priority   new priority, 1 to 5
     * @return the goal
     * @throws UnknownGoalException if there is no such goal for this account
     */
    @Transactional
    public StudyGoalView revise(UUID accountId, UUID goalId, LocalDate targetDate, int priority) {
        access.requireProcessable(accountId);
        StudyGoal goal = requireOwned(accountId, goalId);
        goal.revise(targetDate, priority);
        return PlanningViews.of(goal);
    }

    /**
     * Closes a goal as achieved.
     *
     * @param accountId student
     * @param goalId    goal to close
     * @return the goal
     * @throws UnknownGoalException if there is no such goal for this account
     */
    @Transactional
    public StudyGoalView achieve(UUID accountId, UUID goalId) {
        access.requireProcessable(accountId);
        StudyGoal goal = requireOwned(accountId, goalId);
        goal.achieve(clock.instant());
        return PlanningViews.of(goal);
    }

    /**
     * Closes a goal as abandoned.
     *
     * <p>Not a deletion. What a student gave up on halfway through is data about what people
     * actually pursue, and the instant they stopped is the interesting part of it.
     *
     * @param accountId student
     * @param goalId    goal to close
     * @return the goal
     * @throws UnknownGoalException if there is no such goal for this account
     */
    @Transactional
    public StudyGoalView abandon(UUID accountId, UUID goalId) {
        access.requireProcessable(accountId);
        StudyGoal goal = requireOwned(accountId, goalId);
        goal.abandon(clock.instant());
        return PlanningViews.of(goal);
    }

    private void requireKnownSubject(UUID subjectId) {
        // The catalogue is a curated global list of the order of dozens, returned whole by
        // design, so membership is answered from it rather than by asking curriculum for a
        // lookup it does not offer.
        boolean known = catalog.subjects().stream()
                .map(SubjectView::id)
                .anyMatch(subjectId::equals);
        if (!known) {
            throw new UnknownSubjectException();
        }
    }

    private void requireNoActiveGoal(UUID accountId, UUID subjectId) {
        goals.findByAccountIdAndSubjectIdAndStatus(accountId, subjectId, GoalStatus.ACTIVE)
                .ifPresent(existing -> {
                    throw new DuplicateGoalException();
                });
    }

    private StudyGoal requireOwned(UUID accountId, UUID goalId) {
        return goals.findById(goalId)
                .filter(goal -> goal.accountId().equals(accountId))
                .orElseThrow(UnknownGoalException::new);
    }
}
