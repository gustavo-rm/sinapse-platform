package br.com.sinapse.platform.planning.orchestration;

import br.com.sinapse.platform.planning.api.GenerationRequestStatus;
import br.com.sinapse.platform.planning.api.GenerationRequestView;
import br.com.sinapse.platform.planning.api.PlanGenerationFailure;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A generation job, as the API returns it.
 *
 * @param id            identifier to poll
 * @param status        where the job stands
 * @param horizonStart  first day of the horizon it is planning, inclusive
 * @param horizonEnd    last day of that horizon
 * @param requestedAt   when it was asked for
 * @param startedAt     when a worker last picked it up, or {@code null}
 * @param finishedAt    when it finished, or {@code null}
 * @param attemptCount  how many times it has been attempted
 * @param failureReason which kind of failure ended it, or {@code null}. A value from a closed
 *                      set and never a message: what actually went wrong is in the log
 * @param planId        the plan it produced, or {@code null} until it is ready
 * @param progress      how far along it is, or {@code null} for indeterminate. Always absent in
 *                      this version — the core reports no progress and nothing invents a
 *                      percentage (decision F4), so the client shows elapsed time instead
 */
@Schema(description = "A plan generation job")
public record GenerationRequestResponse(
        UUID id,
        GenerationRequestStatus status,
        LocalDate horizonStart,
        LocalDate horizonEnd,
        Instant requestedAt,
        Instant startedAt,
        Instant finishedAt,
        int attemptCount,
        PlanGenerationFailure failureReason,
        UUID planId,
        Double progress) {

    /**
     * @param view job to render
     * @return the response body
     */
    static GenerationRequestResponse of(GenerationRequestView view) {
        return new GenerationRequestResponse(view.id(), view.status(), view.horizonStart(),
                view.horizonEnd(), view.requestedAt(), view.startedAt(), view.finishedAt(),
                view.attemptCount(), view.failureReason(), view.planId(), view.progress());
    }
}
