package br.com.sinapse.platform.planning.orchestration;

import br.com.sinapse.platform.curriculum.api.CatalogRevisions;
import br.com.sinapse.platform.identity.api.CurrentAccount;
import br.com.sinapse.platform.planning.internal.service.GenerationRequestService;
import br.com.sinapse.platform.planning.internal.web.PlanningRoutes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Asking for a plan, and finding out whether it is ready.
 *
 * <p><strong>Creation returns at once and never waits for the core.</strong> The optimisation
 * is CPU-bound and runs for seconds to minutes; a synchronous answer would be unworkable
 * whatever the topology, which is why ADR 0002 made this a job. The client polls, and shows an
 * explicit waiting state.
 *
 * <p>It lives in the orchestration layer rather than in {@code planning.internal.web} because
 * queuing a job records which revision of the curated catalogue is in effect, and that is a
 * fact from another context. Composing two contexts is what this package is for.
 *
 * <p>Creation is rate limited per account, in the filter chain, before any of this runs. ADR
 * 0009 names it: each run costs minutes of CPU, so the limit is resource control rather than
 * interface polish, and it is the second half of the partial index that allows one unfinished
 * job at a time.
 */
@RestController
@Tag(name = "Plan generation", description = "Asking the optimiser for a plan")
public class GenerationRequestController {

    private final GenerationRequestService requests;
    private final CatalogRevisions catalogRevisions;

    /**
     * @param requests         the job's state machine
     * @param catalogRevisions which curated state of the catalogue is in effect
     */
    public GenerationRequestController(GenerationRequestService requests,
            CatalogRevisions catalogRevisions) {
        this.requests = requests;
        this.catalogRevisions = catalogRevisions;
    }

    /**
     * Asks for a plan.
     *
     * @return the queued job, to be polled
     */
    @PostMapping(value = PlanningRoutes.GENERATION_REQUESTS,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Asks the optimiser for a plan",
            description = "Returns immediately with a job to poll; it never waits for the core. "
                    + "The horizon is configured and is not the client's to choose. One "
                    + "unfinished job per account, and availability and at least one goal have "
                    + "to exist first.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Job queued"),
            @ApiResponse(responseCode = "403", description = "This account's learning data may "
                    + "not be processed",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "A job is already running, or there "
                    + "is nothing to plan yet",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))})
    public ResponseEntity<GenerationRequestResponse> request() {
        UUID accountId = CurrentAccount.require().accountId();
        return ResponseEntity.status(HttpStatus.CREATED).body(GenerationRequestResponse.of(
                requests.queue(accountId, catalogRevisions.currentRevisionId().orElse(null))));
    }

    /**
     * How a job is getting on.
     *
     * @param requestId job
     * @return its state, and the plan it produced once there is one
     */
    @GetMapping(value = PlanningRoutes.GENERATION_REQUESTS + "/{requestId}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "The state of a generation job",
            description = "Polled, because there is no event notification in this version. "
                    + "Once the status is READY the response carries the identifier of the plan. "
                    + "The progress field is absent whenever it is indeterminate, which in this "
                    + "version is always: the core reports none, and nothing here invents one.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The job"),
            @ApiResponse(responseCode = "404", description = "No such job for this account",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))})
    public GenerationRequestResponse state(@PathVariable UUID requestId) {
        return GenerationRequestResponse.of(
                requests.require(CurrentAccount.require().accountId(), requestId));
    }
}
