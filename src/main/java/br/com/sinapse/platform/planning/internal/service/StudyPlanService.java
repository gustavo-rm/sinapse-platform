package br.com.sinapse.platform.planning.internal.service;

import br.com.sinapse.platform.planning.api.PlanStatus;
import br.com.sinapse.platform.planning.api.PlannedSessionKind;
import br.com.sinapse.platform.planning.api.StudyPlanView;
import br.com.sinapse.platform.planning.internal.domain.StudyPlan;
import br.com.sinapse.platform.planning.internal.error.UnknownPlanException;
import br.com.sinapse.platform.planning.internal.persistence.StudyPlanRepository;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Storing a generated plan, and the supersession that comes with it.
 *
 * <p><strong>Nothing here generates anything.</strong> The job, the snapshot and the core
 * client are the next step of the build; this is where a plan the core produced is written
 * down, and it lives in this module rather than in whatever calls it because supersession is a
 * rule about plans and not about the thing that happened to make one.
 *
 * <p>There is deliberately no route to this. A plan comes from the generation job and from
 * nowhere else: a plan written by hand would have no snapshot behind it and would therefore be
 * unreproducible, which is the one property ADR 0007 exists to protect.
 *
 * <p><strong>Why storing a plan is three writes and not one.</strong> The partial index allows
 * one active plan per account, and the successor's row has to exist before the plan it
 * replaced may point at it. Neither order works on its own, so: the outgoing plan is marked
 * superseded and flushed, which frees the index; the new plan is inserted; the outgoing plan
 * is then pointed at it. The trigger on {@code study_plan} permits exactly these fields to
 * move and refuses everything else.
 */
@Service
public class StudyPlanService {

    private final StudyPlanRepository plans;
    private final EntityManager entityManager;
    private final PlanningAccess access;
    private final Clock clock;

    /**
     * @param plans         plans
     * @param entityManager persistence context, used to insert a plan rather than merge one
     * @param access        the access gate of this module
     * @param clock         application clock
     */
    public StudyPlanService(StudyPlanRepository plans, EntityManager entityManager,
            PlanningAccess access, Clock clock) {
        this.plans = plans;
        this.entityManager = entityManager;
        this.access = access;
        this.clock = clock;
    }

    /**
     * Records a plan the core produced and supersedes whatever it replaces.
     *
     * @param accountId           student
     * @param generationRequestId job that produced it
     * @param horizonStart        first day of the horizon, inclusive
     * @param horizonEnd          last day of the horizon
     * @param fitness             metrics the core reported, or {@code null}
     * @param sessions            the sessions it scheduled, in sequence
     * @return the stored plan
     */
    @Transactional
    public StudyPlanView store(UUID accountId, UUID generationRequestId, LocalDate horizonStart,
            LocalDate horizonEnd, Map<String, Object> fitness, List<ScheduledSession> sessions) {

        access.requireProcessable(accountId);
        Instant now = clock.instant();

        Optional<StudyPlan> outgoing = plans.findByAccountIdAndStatus(accountId, PlanStatus.ACTIVE);
        outgoing.ifPresent(previous -> {
            previous.supersede(now);
            // The status has to reach the database before the replacement is inserted.
            // Hibernate orders inserts ahead of updates, so without this the new plan arrives
            // while the old one still looks active, and ux_plan_active — doing exactly its job
            // — refuses it.
            entityManager.flush();
        });

        StudyPlan plan = new StudyPlan(UUID.randomUUID(), accountId, generationRequestId,
                horizonStart, horizonEnd, fitness, now);
        sessions.forEach(session -> plan.schedule(session.topicId(), session.kind(),
                session.scheduledStart(), session.durationMinutes(), session.sequenceIndex()));

        // Inserted, not merged. A plan is written once and this one is new, and a merge would
        // try to reattach its planned sessions — rows that do not exist yet — instead of
        // cascading the insert to them.
        entityManager.persist(plan);
        entityManager.flush();

        // Only now does the successor exist, so only now may the foreign key point at it.
        outgoing.ifPresent(previous -> previous.supersededBy(plan.id()));
        return PlanningViews.of(plan);
    }

    /**
     * A plan of the caller.
     *
     * @param accountId student
     * @param planId    plan
     * @return the plan
     * @throws UnknownPlanException if there is no such plan for this account
     */
    @Transactional(readOnly = true)
    public StudyPlanView require(UUID accountId, UUID planId) {
        access.requireProcessable(accountId);
        return plans.findById(planId)
                .filter(plan -> plan.accountId().equals(accountId))
                .map(PlanningViews::of)
                .orElseThrow(UnknownPlanException::new);
    }

    /**
     * One session of a plan being stored, as the core reported it.
     *
     * <p>Not published from {@code api}: nothing outside this module stores a plan, and a type
     * on the published surface would suggest something might.
     *
     * @param topicId         topic to be studied
     * @param kind            new ground or going back over it
     * @param scheduledStart  when the core placed it
     * @param durationMinutes how long it allowed for it
     * @param sequenceIndex   its position in the plan, unique within the plan
     */
    public record ScheduledSession(
            UUID topicId,
            PlannedSessionKind kind,
            Instant scheduledStart,
            int durationMinutes,
            int sequenceIndex) {
    }
}
