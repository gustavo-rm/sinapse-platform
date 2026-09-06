package br.com.sinapse.platform.planning.internal.web;

import br.com.sinapse.platform.identity.api.CurrentAccount;
import br.com.sinapse.platform.planning.api.PlanningDirectory;
import br.com.sinapse.platform.planning.api.StudyPlanView;
import br.com.sinapse.platform.planning.internal.error.UnknownPlanException;
import br.com.sinapse.platform.planning.internal.service.PlanningAccess;
import br.com.sinapse.platform.planning.internal.service.ScheduleWindow;
import br.com.sinapse.platform.planning.internal.service.StudyPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The caller's plans and the sessions in them.
 *
 * <p><strong>Read-only, and deliberately so.</strong> There is no route that creates or edits a
 * plan. A plan comes from the generation job and from nowhere else: one written by hand would
 * have no snapshot, no core version, no parameters and no seed behind it, and could therefore
 * never be regenerated — which is the one property ADR 0007 exists to protect.
 */
@RestController
@Tag(name = "Study plans", description = "What the optimisation core scheduled")
public class StudyPlanController {

    private final PlanningDirectory directory;
    private final StudyPlanService plans;
    private final PlanningAccess access;
    private final ScheduleWindow window;

    /**
     * @param directory reads of this module
     * @param plans     plan use cases, for the ownership check on a plan read by identifier
     * @param access    the access gate of this module
     * @param window    the bound on how wide a schedule may be asked for
     */
    public StudyPlanController(PlanningDirectory directory, StudyPlanService plans,
            PlanningAccess access, ScheduleWindow window) {
        this.directory = directory;
        this.plans = plans;
        this.access = access;
        this.window = window;
    }

    /**
     * The plan currently in force.
     *
     * @return the active plan
     */
    @GetMapping(value = PlanningRoutes.CURRENT_PLAN, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "The caller's active plan",
            description = "At most one exists. A student with no plan yet has not run the "
                    + "generation job, or its last run has not finished.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The active plan"),
            @ApiResponse(responseCode = "404", description = "There is no active plan",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))})
    public StudyPlanResponse current() {
        UUID accountId = callerId();
        access.requireProcessable(accountId);
        // Thrown rather than answered with an empty 404, so that the body is the problem
        // document every other error in this API is.
        return directory.activePlanOf(accountId)
                .map(StudyPlanResponse::of)
                .orElseThrow(UnknownPlanException::new);
    }

    /**
     * Every plan the caller has had.
     *
     * @return the plans, most recent first
     */
    @GetMapping(value = PlanningRoutes.STUDY_PLANS, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Lists the caller's plans",
            description = "The supersession chain is readable from the result: each superseded "
                    + "plan carries the identifier of the one that replaced it, and when it was "
                    + "replaced. Returned whole, with a hard ceiling, because re-planning is "
                    + "manual and this list grows in single figures.")
    @ApiResponse(responseCode = "200", description = "The plans")
    public List<StudyPlanResponse> history() {
        UUID accountId = callerId();
        access.requireProcessable(accountId);
        return directory.planHistoryOf(accountId).stream().map(StudyPlanResponse::of).toList();
    }

    /**
     * One plan of the caller.
     *
     * @param planId plan
     * @return the plan
     */
    @GetMapping(value = PlanningRoutes.STUDY_PLANS + "/{planId}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Reads one of the caller's plans")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The plan"),
            @ApiResponse(responseCode = "404", description = "No such plan for this account",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))})
    public StudyPlanResponse plan(@PathVariable UUID planId) {
        return StudyPlanResponse.of(plans.require(callerId(), planId));
    }

    /**
     * The sessions of one plan.
     *
     * @param planId plan
     * @return its sessions, in the order the core sequenced them
     */
    @GetMapping(value = PlanningRoutes.STUDY_PLANS + "/{planId}/sessions",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Lists the sessions of one plan",
            description = "The whole plan, in sequence. This is how the sessions of a superseded "
                    + "plan are read: they are preserved untouched, because executed study "
                    + "sessions reference them.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The sessions"),
            @ApiResponse(responseCode = "404", description = "No such plan for this account",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))})
    public List<PlannedSessionResponse> sessionsOfPlan(@PathVariable UUID planId) {
        StudyPlanView plan = plans.require(callerId(), planId);
        return directory.sessionsOfPlan(plan.id()).stream()
                .map(PlannedSessionResponse::of)
                .toList();
    }

    /**
     * The caller's scheduled sessions over a date range.
     *
     * @param from start of the window, inclusive
     * @param to   end of the window, exclusive
     * @return the sessions of the active plan that start within it, earliest first
     */
    @GetMapping(value = PlanningRoutes.PLANNED_SESSIONS,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Lists the caller's scheduled sessions in a window",
            description = "The plan in force, not every plan the student has ever had: this is "
                    + "what there is to do. The window is mandatory and its span is capped, "
                    + "because planned sessions accumulate with every plan and there is no "
                    + "generic pagination in this API.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The sessions"),
            @ApiResponse(responseCode = "400", description = "The window is empty, inverted or "
                    + "wider than the accepted maximum",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))})
    public List<PlannedSessionResponse> plannedSessions(@RequestParam Instant from,
            @RequestParam Instant to) {

        UUID accountId = callerId();
        access.requireProcessable(accountId);
        window.require(from, to);
        return directory.plannedSessionsOf(accountId, from, to).stream()
                .map(PlannedSessionResponse::of)
                .toList();
    }

    private static UUID callerId() {
        return CurrentAccount.require().accountId();
    }
}
