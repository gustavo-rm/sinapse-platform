package br.com.sinapse.platform.readmodel.internal.web;

import br.com.sinapse.platform.identity.api.CurrentAccount;
import br.com.sinapse.platform.readmodel.api.DailyAgendaView;
import br.com.sinapse.platform.readmodel.api.StudentStateView;
import br.com.sinapse.platform.readmodel.internal.service.AgendaReadModel;
import br.com.sinapse.platform.readmodel.internal.service.StudentStateReadModel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The two screens a student opens for themselves.
 *
 * <p>Neither route takes an account identifier. Both act on the caller, which is what makes it
 * impossible to write one that reads somebody else — the same reason the data subject rights
 * routes are shaped this way.
 */
@RestController
@Tag(name = "My screens", description = "The composed reads a student makes about themselves")
public class MyReadModelController {

    private final StudentStateReadModel state;
    private final AgendaReadModel agenda;

    /**
     * @param state  the initial screen
     * @param agenda the day's agenda
     */
    public MyReadModelController(StudentStateReadModel state, AgendaReadModel agenda) {
        this.state = state;
        this.agenda = agenda;
    }

    /**
     * Everything the initial screen needs.
     *
     * @return the caller's state
     */
    @GetMapping(value = ReadModelRoutes.MY_STATE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Everything the initial screen needs, in one call",
            description = "Composed from identity, planning and the learning record. A holder "
                    + "whose learning data may not be processed still gets an answer — that is "
                    + "how they learn the account is suspended — but the plan, the job, the "
                    + "open session and the readiness flag come back empty.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The caller's state"),
            @ApiResponse(responseCode = "401", description = "No usable session on the request",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))})
    public StudentStateView state() {
        return state.of(callerId());
    }

    /**
     * The caller's agenda over a window.
     *
     * @param from start of the window, inclusive
     * @param to   end of the window, exclusive
     * @return the days that have something scheduled
     */
    @GetMapping(value = ReadModelRoutes.MY_AGENDA, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "The caller's planned sessions of a window, grouped by day",
            description = "Each scheduled slot carries the topic and subject names and the "
                    + "session that executed it, if any. Days are grouped in the holder's own "
                    + "time zone. The window is mandatory and its span is capped: this API has "
                    + "no generic pagination, by decision.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The agenda"),
            @ApiResponse(responseCode = "400", description = "The window is empty, inverted or "
                    + "wider than the server will answer",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "No usable session on the request",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "The account's learning data may not "
                    + "be processed",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))})
    public DailyAgendaView agenda(@RequestParam Instant from, @RequestParam Instant to) {
        return agenda.of(callerId(), from, to);
    }

    private static UUID callerId() {
        return CurrentAccount.require().accountId();
    }
}
