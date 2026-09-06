package br.com.sinapse.platform.planning.internal.web;

import br.com.sinapse.platform.identity.api.CurrentAccount;
import br.com.sinapse.platform.planning.api.PlanningDirectory;
import br.com.sinapse.platform.planning.internal.service.GoalService;
import br.com.sinapse.platform.planning.internal.service.PlanningAccess;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The caller's goals.
 *
 * <p>A goal is a subject. There is no route that adds or removes a topic from one, by decision
 * L3: for the exam preparation this is built for the usual scope is the whole syllabus, and
 * the optimisation core decides the order and what fits.
 *
 * <p>Nothing here deletes. A goal is achieved or abandoned, and either way the instant is
 * written down — what a student gave up on in week three is data about what people actually
 * pursue.
 */
@RestController
@Tag(name = "Goals", description = "What the student intends to get through")
public class GoalController {

    private final GoalService goals;
    private final PlanningDirectory directory;
    private final PlanningAccess access;

    /**
     * @param goals     goal use cases
     * @param directory reads of this module
     * @param access    the access gate of this module
     */
    public GoalController(GoalService goals, PlanningDirectory directory, PlanningAccess access) {
        this.goals = goals;
        this.directory = directory;
        this.access = access;
    }

    /**
     * Sets a goal.
     *
     * @param request the subject, the date and the priority
     * @return the goal
     */
    @PostMapping(value = PlanningRoutes.GOALS, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Sets a goal for a subject",
            description = "The target date is a prioritisation constraint and not the plan "
                    + "horizon: preparation can run for a year, and generating a year of plan "
                    + "would be expensive and mostly wrong. At most one active goal per subject.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Goal set"),
            @ApiResponse(responseCode = "404", description = "No such subject",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "There is already an active goal for "
                    + "that subject",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))})
    public ResponseEntity<GoalResponse> set(@Valid @RequestBody GoalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(GoalResponse.of(
                goals.set(callerId(), request.subjectId(), request.targetDate(),
                        request.priorityOrDefault())));
    }

    /**
     * The caller's goals.
     *
     * @return every goal set, closed ones included, most pressing first
     */
    @GetMapping(value = PlanningRoutes.GOALS, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Lists the caller's goals",
            description = "Closed goals included, because what was abandoned is part of what the "
                    + "student has decided. The status says which is which.")
    @ApiResponse(responseCode = "200", description = "The goals")
    public List<GoalResponse> list() {
        UUID accountId = callerId();
        access.requireProcessable(accountId);
        return directory.goalsOf(accountId).stream().map(GoalResponse::of).toList();
    }

    /**
     * Changes what a goal asks for.
     *
     * @param goalId  goal to revise
     * @param request the new date and priority
     * @return the goal
     */
    @PatchMapping(value = PlanningRoutes.GOALS + "/{goalId}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Revises an active goal",
            description = "The subject cannot change: a goal about another subject is another "
                    + "goal, and repointing one would move the record of an abandoned subject "
                    + "onto a new one.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Goal revised"),
            @ApiResponse(responseCode = "404", description = "No such goal for this account",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "The goal has already been closed",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))})
    public GoalResponse revise(@PathVariable UUID goalId,
            @Valid @RequestBody GoalRevisionRequest request) {

        return GoalResponse.of(goals.revise(callerId(), goalId, request.targetDate(),
                request.priority()));
    }

    /**
     * Closes a goal as achieved.
     *
     * @param goalId goal to close
     * @return the goal
     */
    @PostMapping(value = PlanningRoutes.GOALS + "/{goalId}/achievement",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Marks a goal as achieved")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Goal achieved"),
            @ApiResponse(responseCode = "404", description = "No such goal for this account",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "The goal has already been closed",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))})
    public GoalResponse achieve(@PathVariable UUID goalId) {
        return GoalResponse.of(goals.achieve(callerId(), goalId));
    }

    /**
     * Closes a goal as abandoned.
     *
     * @param goalId goal to close
     * @return the goal
     */
    @PostMapping(value = PlanningRoutes.GOALS + "/{goalId}/abandonment",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Abandons a goal",
            description = "Not a deletion. The goal stays, with the instant the student stopped "
                    + "pursuing it, and the subject becomes free for a new goal.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Goal abandoned"),
            @ApiResponse(responseCode = "404", description = "No such goal for this account",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "The goal has already been closed",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))})
    public GoalResponse abandon(@PathVariable UUID goalId) {
        return GoalResponse.of(goals.abandon(callerId(), goalId));
    }

    private static UUID callerId() {
        return CurrentAccount.require().accountId();
    }
}
