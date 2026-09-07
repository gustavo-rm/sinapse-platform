package br.com.sinapse.platform.readmodel.internal.web;

import br.com.sinapse.platform.identity.api.CurrentAccount;
import br.com.sinapse.platform.readmodel.api.PlanSummaryView;
import br.com.sinapse.platform.readmodel.internal.service.PlanSummaryReadModel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * A plan seen whole.
 *
 * <p>Sits under the plan's own path because it is another way of looking at the same resource.
 * A plan belonging to somebody else answers the same way as one that does not exist, so the
 * route cannot be used to find out which plan identifiers are real.
 */
@RestController
@Tag(name = "My screens", description = "The composed reads a student makes about themselves")
public class PlanSummaryController {

    private final PlanSummaryReadModel summaries;

    /**
     * @param summaries the plan summary read model
     */
    public PlanSummaryController(PlanSummaryReadModel summaries) {
        this.summaries = summaries;
    }

    /**
     * A summary of one of the caller's plans.
     *
     * @param planId plan to summarise
     * @return the summary
     */
    @GetMapping(value = ReadModelRoutes.PLAN_SUMMARY_TEMPLATE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Summarises one of the caller's plans",
            description = "Total scheduled time, the breakdown by subject, and adherence over "
                    + "the part of the plan that has already fallen due. A session still in the "
                    + "future is not counted as missed, and the ratio is absent rather than zero "
                    + "when nothing has fallen due yet.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The summary"),
            @ApiResponse(responseCode = "401", description = "No usable session on the request",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "The account's learning data may not "
                    + "be processed",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "No such plan for this caller",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))})
    public PlanSummaryView summary(@PathVariable UUID planId) {
        return summaries.of(CurrentAccount.require().accountId(), planId);
    }
}
