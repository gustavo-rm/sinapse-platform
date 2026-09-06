package br.com.sinapse.platform.planning.internal.web;

import br.com.sinapse.platform.identity.api.CurrentAccount;
import br.com.sinapse.platform.planning.api.AvailabilityWindowView;
import br.com.sinapse.platform.planning.api.PlanningDirectory;
import br.com.sinapse.platform.planning.internal.service.AvailabilityService;
import br.com.sinapse.platform.planning.internal.service.PlanningAccess;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The caller's weekly availability.
 *
 * <p>There is no route that edits a window. Changing a routine is closing the window that
 * stopped applying and declaring a new one, because a plan was generated against what the
 * window said while it applied, and an edit in place would leave that plan explained by an
 * availability the student no longer has.
 */
@RestController
@Tag(name = "Availability", description = "When the student is free to study")
public class AvailabilityController {

    private final AvailabilityService availability;
    private final PlanningDirectory directory;
    private final PlanningAccess access;

    /**
     * @param availability availability use cases
     * @param directory    reads of this module
     * @param access       the access gate of this module
     */
    public AvailabilityController(AvailabilityService availability, PlanningDirectory directory,
            PlanningAccess access) {
        this.availability = availability;
        this.directory = directory;
        this.access = access;
    }

    /**
     * Declares a window.
     *
     * @param request the window
     * @return the declared window
     */
    @PostMapping(value = PlanningRoutes.AVAILABILITY, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Declares a weekly availability window",
            description = "Times are local to the account's own zone and carry no offset. A "
                    + "window that would overlap one already declared for the same weekday is "
                    + "refused: overlapping windows would have whatever allocates study hours "
                    + "count the same hour twice.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Window declared"),
            @ApiResponse(responseCode = "400", description = "The window closes before it opens, "
                    + "or stops applying before it applied",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "This account's learning data may "
                    + "not be processed",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "It would overlap an existing window",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))})
    public ResponseEntity<AvailabilityResponse> declare(
            @Valid @RequestBody AvailabilityRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED).body(AvailabilityResponse.of(
                availability.declare(callerId(), request.dayOfWeek(), request.startTime(),
                        request.endTime(), request.effectiveFrom(), request.effectiveUntil())));
    }

    /**
     * The caller's windows.
     *
     * @param on day to filter by, or {@code null} for every window ever declared
     * @return the windows, ordered by day and then by start time
     */
    @GetMapping(value = PlanningRoutes.AVAILABILITY, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Lists the caller's availability windows",
            description = "Without a date this returns every window ever declared, closed ones "
                    + "included, which is what lets a client show the routine and its history. "
                    + "The date is supplied by the caller rather than defaulted to today, "
                    + "because these are local times in the account's own zone and the server "
                    + "does not decide what day it is for somebody else.")
    @ApiResponse(responseCode = "200", description = "The windows")
    public List<AvailabilityResponse> list(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate on) {

        UUID accountId = callerId();
        access.requireProcessable(accountId);
        List<AvailabilityWindowView> windows = on == null
                ? directory.availabilityOf(accountId)
                : directory.availabilityOn(accountId, on);
        return windows.stream().map(AvailabilityResponse::of).toList();
    }

    /**
     * Closes a window.
     *
     * @param windowId window to close
     * @param request  the day it stops applying
     * @return the closed window
     */
    @PostMapping(value = PlanningRoutes.AVAILABILITY + "/{windowId}/closure",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Closes an availability window",
            description = "The window stays on record and keeps saying what it said for the "
                    + "period it covered. That is what a past plan was generated against.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Window closed"),
            @ApiResponse(responseCode = "404", description = "No such window for this account",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "The window is already closed",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))})
    public AvailabilityResponse close(@PathVariable UUID windowId,
            @Valid @RequestBody AvailabilityClosureRequest request) {

        return AvailabilityResponse.of(
                availability.close(callerId(), windowId, request.effectiveUntil()));
    }

    private static UUID callerId() {
        return CurrentAccount.require().accountId();
    }
}
