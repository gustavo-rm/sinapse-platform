package br.com.sinapse.platform.learningrecord.internal.web;

import br.com.sinapse.platform.identity.api.CurrentAccount;
import br.com.sinapse.platform.learningrecord.api.StudyHistory;
import br.com.sinapse.platform.learningrecord.internal.error.UnknownSessionException;
import br.com.sinapse.platform.learningrecord.internal.service.HistoryWindow;
import br.com.sinapse.platform.learningrecord.internal.service.LearningRecordAccess;
import br.com.sinapse.platform.learningrecord.internal.service.StudySessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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
 * The caller's own study sessions: starting one, closing it, and reading what has been
 * recorded.
 *
 * <p>Every route here answers about the caller and nobody else. There is no path parameter
 * for an account, which is what makes it impossible to write a route that reads a student's
 * history because the caller happened to know their identifier. A teacher reading a student
 * goes through the read models, where {@code TeacherAccessPolicy} is asked.
 *
 * <p>Starting and recording after the fact are two routes because they are two operations,
 * not two kinds of session: what they produce differs only in {@code durationSource}, and a
 * client cannot get a self-reported duration through the timed route or a measured one
 * through the retroactive route. That is decision F5 expressed as a shape rather than as a
 * convention somebody has to remember.
 */
@RestController
@Tag(name = "Study sessions", description = "Recording what was actually studied")
public class StudySessionController {

    private final StudySessionService sessions;
    private final StudyHistory history;
    private final LearningRecordAccess access;
    private final HistoryWindow window;

    /**
     * @param sessions session lifecycle
     * @param history  history reads
     * @param access   the access gate of this module
     * @param window   the bound on how wide a history may be asked for
     */
    public StudySessionController(StudySessionService sessions, StudyHistory history,
            LearningRecordAccess access, HistoryWindow window) {
        this.sessions = sessions;
        this.history = history;
        this.access = access;
        this.window = window;
    }

    /**
     * Starts a session.
     *
     * @param request what is about to be studied
     * @return the running session
     */
    @PostMapping(value = LearningRecordRoutes.SESSIONS,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Starts a study session",
            description = "The application times it, which is what makes the recorded duration "
                    + "measured rather than stated. At most one session runs at a time: the "
                    + "screen is expected to offer resuming the open one instead of being "
                    + "refused here.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Session started"),
            @ApiResponse(responseCode = "403", description = "This account's learning data may "
                    + "not be processed",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "No such topic",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "A session is already running",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))})
    public ResponseEntity<StudySessionResponse> start(
            @Valid @RequestBody StartSessionRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED).body(StudySessionResponse.of(
                sessions.start(callerId(), request.topicId(), request.plannedSessionId(),
                        request.kind(), request.plannedDurationMinutes())));
    }

    /**
     * Closes a running session as completed.
     *
     * @param sessionId session to close
     * @param request   the recall rating, and a duration only if the timer was wrong
     * @return the closed session
     */
    @PostMapping(value = LearningRecordRoutes.SESSIONS + "/{sessionId}/completion",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Completes a running session",
            description = "The recall rating is required and exists only here: a session that "
                    + "was abandoned has none, and a session already closed cannot acquire one.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Session completed"),
            @ApiResponse(responseCode = "404", description = "No such session for this account",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "The session is already closed. A "
                    + "correction is a new record, not an edit",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))})
    public StudySessionResponse complete(@PathVariable UUID sessionId,
            @Valid @RequestBody CompleteSessionRequest request) {

        return StudySessionResponse.of(sessions.complete(callerId(), sessionId,
                request.recallRating(), request.actualDurationMinutes()));
    }

    /**
     * Closes a running session as abandoned.
     *
     * @param sessionId session to close
     * @return the closed session
     */
    @PostMapping(value = LearningRecordRoutes.SESSIONS + "/{sessionId}/abandonment",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Abandons a running session",
            description = "No rating and no duration. The student stopped, and the elapsed time "
                    + "of a session somebody walked away from measures nothing; what the record "
                    + "says is that it was opened and not finished.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Session abandoned"),
            @ApiResponse(responseCode = "404", description = "No such session for this account",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "The session is already closed",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))})
    public StudySessionResponse abandon(@PathVariable UUID sessionId) {
        return StudySessionResponse.of(sessions.abandon(callerId(), sessionId));
    }

    /**
     * Records a session that already happened.
     *
     * @param request what was studied, when, and for how long
     * @return the recorded session
     */
    @PostMapping(value = LearningRecordRoutes.RETROACTIVE_ENTRIES,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Records a study session after the fact",
            description = "The marked exception of decision F5. The duration is always stored as "
                    + "self-reported, so the two sets can be analysed apart. How far back this "
                    + "may reach is configured, and the route is rate limited per account.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Session recorded"),
            @ApiResponse(responseCode = "400", description = "The session is in the future or "
                    + "older than the accepted limit",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "No such topic",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))})
    public ResponseEntity<StudySessionResponse> recordRetroactively(
            @Valid @RequestBody RetroactiveSessionRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED).body(StudySessionResponse.of(
                sessions.recordRetroactively(callerId(), request.topicId(),
                        request.plannedSessionId(), request.kind(), request.startedAt(),
                        request.actualDurationMinutes(), request.recallRating())));
    }

    /**
     * The caller's sessions within a window.
     *
     * @param from start of the window, inclusive
     * @param to   end of the window, exclusive
     * @return the sessions that started within it, most recent first
     */
    @GetMapping(value = LearningRecordRoutes.SESSIONS, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Lists the caller's study sessions in a window",
            description = "The window is mandatory and its span is capped. A history grows "
                    + "without limit and there is no generic pagination in this API, so an "
                    + "unbounded read would be a way of asking the server for everything.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The sessions"),
            @ApiResponse(responseCode = "400", description = "The window is empty, inverted or "
                    + "wider than the accepted maximum",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))})
    public List<StudySessionResponse> list(@RequestParam Instant from, @RequestParam Instant to) {
        UUID accountId = callerId();
        access.requireProcessable(accountId);
        window.require(from, to);
        return history.sessionsOf(accountId, from, to).stream()
                .map(StudySessionResponse::of)
                .toList();
    }

    /**
     * The session the caller currently has running.
     *
     * @return the running session, or 404 when there is none
     */
    @GetMapping(value = LearningRecordRoutes.CURRENT_SESSION,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "The caller's running session",
            description = "At most one exists. The initial screen reads this so that it can "
                    + "offer to resume rather than start a second one and be refused.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The running session"),
            @ApiResponse(responseCode = "404", description = "Nothing is running",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))})
    public StudySessionResponse current() {
        UUID accountId = callerId();
        access.requireProcessable(accountId);
        // Thrown rather than answered with an empty 404, so that the body is the problem
        // document every other error in this API is. A bare status here would be the one
        // response a client has to special-case.
        return history.openSessionOf(accountId)
                .map(StudySessionResponse::of)
                .orElseThrow(UnknownSessionException::new);
    }

    private static UUID callerId() {
        return CurrentAccount.require().accountId();
    }
}
