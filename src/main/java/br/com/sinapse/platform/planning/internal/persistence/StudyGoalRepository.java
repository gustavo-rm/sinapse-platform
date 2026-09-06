package br.com.sinapse.platform.planning.internal.persistence;

import br.com.sinapse.platform.planning.api.GoalStatus;
import br.com.sinapse.platform.planning.internal.domain.StudyGoal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Goals. Never deleted: a goal is achieved or abandoned, and both are recorded. */
public interface StudyGoalRepository extends JpaRepository<StudyGoal, UUID> {

    /**
     * The active goal of an account for a subject.
     *
     * <p>At most one, by the partial index.
     *
     * @param accountId student
     * @param subjectId subject
     * @param status    state being looked for
     * @return the goal, if there is one
     */
    Optional<StudyGoal> findByAccountIdAndSubjectIdAndStatus(UUID accountId, UUID subjectId,
            GoalStatus status);

    /**
     * The goals of an account in one state, most pressing first.
     *
     * @param accountId student
     * @param status    state being looked for
     * @return the goals
     */
    List<StudyGoal> findByAccountIdAndStatusOrderByPriorityDescCreatedAtDesc(UUID accountId,
            GoalStatus status);

    /**
     * Every goal an account has set, most pressing first.
     *
     * @param accountId student
     * @return the goals, closed ones included
     */
    List<StudyGoal> findByAccountIdOrderByPriorityDescCreatedAtDesc(UUID accountId);

    /**
     * Removes every row of this kind belonging to an account.
     *
     * <p>Only ever called from the erasure transaction. ADR 0011 lists this table among the
     * ones that do not survive.
     *
     * @param accountId student whose data is being erased
     * @return how many rows were removed
     */
    @Modifying
    @Query("delete from StudyGoal goal where goal.accountId = :accountId")
    int eraseFor(@Param("accountId") UUID accountId);
}
