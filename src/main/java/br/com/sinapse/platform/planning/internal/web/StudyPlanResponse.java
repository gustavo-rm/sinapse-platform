package br.com.sinapse.platform.planning.internal.web;

import br.com.sinapse.platform.planning.api.PlanStatus;
import br.com.sinapse.platform.planning.api.StudyPlanView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * A plan, as the API returns it.
 *
 * @param id                  identifier
 * @param generationRequestId job that produced it, which is where the snapshot, the core
 *                            version, the parameters and the seed live
 * @param horizonStart        first day of the horizon, inclusive
 * @param horizonEnd          last day of the horizon
 * @param status              in force, or replaced
 * @param fitness             the metrics the core reported, as it reported them
 * @param createdAt           when it was stored
 * @param supersededAt        when it was replaced, or {@code null}
 * @param supersededByPlanId  the plan that replaced it, or {@code null}
 */
@Schema(description = "A generated study plan")
public record StudyPlanResponse(
        UUID id,
        UUID generationRequestId,
        LocalDate horizonStart,
        LocalDate horizonEnd,
        PlanStatus status,
        Map<String, Object> fitness,
        Instant createdAt,
        Instant supersededAt,
        UUID supersededByPlanId) {

    /**
     * Renders a view.
     *
     * <p>The account is left out. Every route of this module answers about the caller, so the
     * field would carry the reader's own identifier back to them and nothing else.
     *
     * @param view plan to render
     * @return the response body
     */
    static StudyPlanResponse of(StudyPlanView view) {
        return new StudyPlanResponse(view.id(), view.generationRequestId(), view.horizonStart(),
                view.horizonEnd(), view.status(), view.fitness(), view.createdAt(),
                view.supersededAt(), view.supersededByPlanId());
    }
}
