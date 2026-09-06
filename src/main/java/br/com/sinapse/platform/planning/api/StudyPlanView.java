package br.com.sinapse.platform.planning.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * A plan the optimisation core produced.
 *
 * <p>Immutable except for its supersession. Editing a plan would destroy the correspondence
 * between it and the snapshot that produced it, and that correspondence is what makes the
 * plan reproducible — a requirement of the thesis rather than audit comfort (ADR 0007).
 *
 * @param id                  identifier
 * @param accountId           student the plan belongs to
 * @param generationRequestId job that produced it. The snapshot, the core version, the
 *                            parameters and the seed hang off that row, and all four together
 *                            are what let this plan be regenerated
 * @param horizonStart        first day of the horizon, inclusive
 * @param horizonEnd          last day of the horizon
 * @param status              in force, or replaced
 * @param fitness             the metrics the core reported, as it reported them. Not
 *                            interpreted here: what the numbers mean belongs to the core's
 *                            own contract, and normalising them would create a second copy of
 *                            a model this module does not own
 * @param createdAt           when it was stored
 * @param supersededAt        when it was replaced, or {@code null}
 * @param supersededByPlanId  the plan that replaced it, or {@code null}
 */
public record StudyPlanView(
        UUID id,
        UUID accountId,
        UUID generationRequestId,
        LocalDate horizonStart,
        LocalDate horizonEnd,
        PlanStatus status,
        Map<String, Object> fitness,
        Instant createdAt,
        Instant supersededAt,
        UUID supersededByPlanId) {

    /** Defensive copy, so that the metrics cannot change under a caller reading them. */
    public StudyPlanView {
        fitness = fitness == null ? null : Map.copyOf(fitness);
    }

    /** Whether this is the plan in force. */
    public boolean isActive() {
        return status == PlanStatus.ACTIVE;
    }
}
