package br.com.sinapse.platform.readmodel.internal.web;

import br.com.sinapse.platform.identity.api.CurrentAccount;
import br.com.sinapse.platform.readmodel.api.ClassroomRosterView;
import br.com.sinapse.platform.readmodel.api.StudentPanelView;
import br.com.sinapse.platform.readmodel.internal.service.ClassroomRosterReadModel;
import br.com.sinapse.platform.readmodel.internal.service.StudentPanelReadModel;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The two screens a teacher opens about their classroom.
 *
 * <p>Both are gated twice: the classroom has to belong to the caller, and each student has to be
 * readable by them at this instant — an active enrollment and a valid {@code
 * INSTITUTION_SHARING} consent, checked on every request. A student who withdraws disappears
 * from the class list and their panel stops answering, immediately and with nothing to
 * propagate; consenting again brings both back, with the enrollment untouched throughout.
 *
 * <p>Every refusal is the same 404. Telling a teacher apart the cases — no such classroom, not
 * yours, that student is not in it, that student has withdrawn — would turn these routes into a
 * way of discovering both which identifiers are real and who has revoked, and the second is
 * itself something the consent governs.
 */
@RestController
@Tag(name = "Classroom screens", description = "What a teacher reads about their classroom")
public class ClassroomReadModelController {

    private final ClassroomRosterReadModel roster;
    private final StudentPanelReadModel panels;

    /**
     * @param roster the class list
     * @param panels one student's panel
     */
    public ClassroomReadModelController(ClassroomRosterReadModel roster, StudentPanelReadModel panels) {
        this.roster = roster;
        this.panels = panels;
    }

    /**
     * The students of a classroom the caller owns.
     *
     * @param classroomId classroom
     * @param from        start of the window, inclusive
     * @param to          end of the window, exclusive
     * @return the students the caller may currently read
     */
    @GetMapping(value = ReadModelRoutes.CLASSROOM_STUDENTS_TEMPLATE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Lists the students of a classroom with adherence and total time",
            description = "Three fields per student, computed on demand over the requested "
                    + "window. Students who have withdrawn their sharing consent are absent "
                    + "rather than present with the figures blanked out. Returned whole, with a "
                    + "hard server-side cap: a classroom is naturally small and this API has no "
                    + "generic pagination.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The class list"),
            @ApiResponse(responseCode = "400", description = "The window is empty, inverted or "
                    + "wider than the server will answer",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "The caller does not hold the "
                    + "teacher role",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "No such classroom for this caller",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))})
    public ClassroomRosterView students(@PathVariable UUID classroomId,
            @RequestParam Instant from, @RequestParam Instant to) {

        return roster.of(CurrentAccount.require().accountId(), classroomId, from, to);
    }

    /**
     * One student's panel.
     *
     * @param classroomId classroom the caller owns
     * @param accountId   student being read
     * @param from        start of the window, inclusive
     * @param to          end of the window, exclusive
     * @return the panel
     */
    @GetMapping(value = ReadModelRoutes.STUDENT_PANEL_TEMPLATE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Reads one student's adherence, effort and recall over a window",
            description = "The student's whole record over the window, not only the part "
                    + "belonging to this teacher's subjects: the scope is integral by decision "
                    + "P1, and the student is told so before accepting the invite. Requires an "
                    + "active enrollment and a valid INSTITUTION_SHARING consent, both read at "
                    + "the moment of the request.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The panel"),
            @ApiResponse(responseCode = "400", description = "The window is empty, inverted or "
                    + "wider than the server will answer",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "The caller does not hold the "
                    + "teacher role",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "The classroom is not the caller's, "
                    + "or the student is not currently readable by them",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))})
    public StudentPanelView panel(@PathVariable UUID classroomId, @PathVariable UUID accountId,
            @RequestParam Instant from, @RequestParam Instant to) {

        return panels.of(CurrentAccount.require().accountId(), classroomId, accountId, from, to);
    }
}
