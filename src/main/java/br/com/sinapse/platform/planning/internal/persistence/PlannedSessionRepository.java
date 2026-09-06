package br.com.sinapse.platform.planning.internal.persistence;

import br.com.sinapse.platform.planning.api.PlanStatus;
import br.com.sinapse.platform.planning.internal.domain.PlannedSession;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Planned sessions.
 *
 * <p>Read-only in practice. They are written by cascade from the plan that owns them and a
 * trigger refuses every update and delete, so nothing here offers either.
 */
public interface PlannedSessionRepository extends JpaRepository<PlannedSession, UUID> {

    /**
     * The sessions of one plan, in the order the core sequenced them.
     *
     * @param planId plan
     * @return its sessions
     */
    @Query("""
            select session
              from PlannedSession session
             where session.plan.id = :planId
             order by session.sequenceIndex
            """)
    List<PlannedSession> findByPlan(@Param("planId") UUID planId);

    /**
     * The sessions of an account's plan in a given state that start within a window.
     *
     * <p>Asked with {@link PlanStatus#ACTIVE}: the day's agenda is about the plan in force,
     * not about every plan the student has ever had.
     *
     * @param accountId student
     * @param status    state of the plan the sessions must belong to
     * @param from      start of the window, inclusive
     * @param to        end of the window, exclusive
     * @return the sessions, earliest first
     */
    @Query("""
            select session
              from PlannedSession session
             where session.plan.accountId = :accountId
               and session.plan.status = :status
               and session.scheduledStart >= :from
               and session.scheduledStart < :to
             order by session.scheduledStart
            """)
    List<PlannedSession> findInWindow(@Param("accountId") UUID accountId,
            @Param("status") PlanStatus status, @Param("from") Instant from,
            @Param("to") Instant to);

    /**
     * The identifiers of the sessions that exist, out of a set.
     *
     * <p>What the consistency check in the orchestration layer asks. It holds a set of
     * references taken from executed study sessions and needs to know which of them still
     * name something — a question about identifiers, so the answer is identifiers and no rows
     * are loaded.
     *
     * @param plannedSessionIds identifiers to look for
     * @return the ones that exist
     */
    @Query("""
            select session.id
              from PlannedSession session
             where session.id in :plannedSessionIds
            """)
    Set<UUID> findExistingIds(@Param("plannedSessionIds") Collection<UUID> plannedSessionIds);
}
