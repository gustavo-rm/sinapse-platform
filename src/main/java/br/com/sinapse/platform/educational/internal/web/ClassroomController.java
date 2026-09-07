package br.com.sinapse.platform.educational.internal.web;

import br.com.sinapse.platform.educational.api.ClassroomView;
import br.com.sinapse.platform.educational.api.EnrollmentView;
import br.com.sinapse.platform.educational.internal.domain.Classroom;
import br.com.sinapse.platform.educational.internal.service.ClassroomService;
import br.com.sinapse.platform.educational.internal.service.EducationalDirectoryService;
import br.com.sinapse.platform.identity.api.CurrentAccount;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Classrooms, from the teacher's side.
 *
 * <p>Every route here requires the teacher role and ownership of the classroom in question.
 * A classroom belonging to somebody else answers as not found rather than as forbidden, so
 * that the routes cannot be walked to find out which identifiers exist.
 */
@RestController
@Tag(name = "Classrooms", description = "Classrooms owned by a teacher")
public class ClassroomController {

    private final ClassroomService classrooms;
    private final EducationalDirectoryService directory;

    /**
     * @param classrooms classroom use cases
     * @param directory  reads of classrooms and enrollments
     */
    public ClassroomController(ClassroomService classrooms, EducationalDirectoryService directory) {
        this.classrooms = classrooms;
        this.directory = directory;
    }

    /**
     * Opens a classroom.
     *
     * @param request name and the subjects carrying an institutional deadline
     * @return the classroom
     */
    @PostMapping(value = EducationalRoutes.CLASSROOMS,
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Opens a classroom",
            description = "The subjects declare which ones carry an institutional deadline, which "
                    + "is input for the optimisation core. They do not decide what the teacher "
                    + "will see: that is the visibility scope, and today it is everything.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Classroom opened"),
            @ApiResponse(responseCode = "403", description = "The caller is not a teacher, or has "
                    + "no teacher record",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))})
    public ResponseEntity<ClassroomResponse> open(@Valid @RequestBody ClassroomRequest request) {
        ClassroomView classroom = classrooms.open(CurrentAccount.require().accountId(),
                request.name().trim(), request.subjectIds());
        return ResponseEntity.status(HttpStatus.CREATED).body(responseFor(classroom, 0));
    }

    /**
     * The caller's classrooms, newest first.
     *
     * @return their classrooms, archived ones included
     */
    @GetMapping(value = EducationalRoutes.CLASSROOMS, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Lists the classrooms the caller owns")
    @ApiResponse(responseCode = "200", description = "The classrooms, archived ones included")
    public List<ClassroomResponse> list() {
        UUID caller = CurrentAccount.require().accountId();
        return directory.classroomsOwnedBy(caller).stream()
                .map(classroom -> responseFor(classroom,
                        directory.activeEnrollmentsIn(classroom.id()).size()))
                .toList();
    }

    /**
     * Archives a classroom, ending its enrollments and revoking its invites.
     *
     * @param classroomId classroom to archive
     * @return the classroom, now archived
     */
    @PostMapping(value = EducationalRoutes.CLASSROOMS + "/{classroomId}/archival",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Archives a classroom",
            description = "Ends every active enrollment and revokes every outstanding invite, in "
                    + "the same transaction. The enrollments are not deleted: they are what "
                    + "justifies the access the teacher had while the classroom was open.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Classroom archived"),
            @ApiResponse(responseCode = "404", description = "No such classroom for this teacher",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "It is already archived",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))})
    public ClassroomResponse archive(@PathVariable UUID classroomId) {
        UUID caller = CurrentAccount.require().accountId();
        classrooms.archive(caller, classroomId);
        return directory.classroom(classroomId)
                .map(classroom -> responseFor(classroom, 0))
                .orElseThrow();
    }

    /**
     * Who is currently enrolled in a classroom.
     *
     * <p>Identifiers and enrollment dates, and no names: identity stores no display name for a
     * student, so there is none to return.
     *
     * <p><strong>Under {@code /enrollments} and not {@code /students}.</strong> Section 3.6 of
     * the API contract gives {@code /classrooms/&#123;id&#125;/students} to the class list, which
     * is a read model composed across modules and returns a different thing: figures per
     * student, and only for the students whose sharing consent is in force at that moment.
     * This one is enrollment management, and it has to keep showing a student who has
     * withdrawn — otherwise the teacher could not remove them. Two answers, so two paths, and
     * the payload here is an enrollment, which is what the path now says.
     *
     * @param classroomId classroom
     * @return its active enrollments
     */
    @GetMapping(value = EducationalRoutes.CLASSROOMS + "/{classroomId}/enrollments",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Lists the enrollments currently open in a classroom",
            description = "Enrollment management, and distinct from the class list of section "
                    + "3.6 of the API contract: it shows every student who is in the classroom, "
                    + "including one who has withdrawn their sharing consent, because removing "
                    + "them has to remain possible.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The active enrollments"),
            @ApiResponse(responseCode = "404", description = "No such classroom for this teacher",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))})
    public List<EnrollmentResponse> students(@PathVariable UUID classroomId) {
        UUID caller = CurrentAccount.require().accountId();
        Classroom classroom = classrooms.requireOwned(caller, classroomId);
        return directory.activeEnrollmentsIn(classroom.id()).stream()
                .map(enrollment -> responseFor(enrollment, classroom.name()))
                .toList();
    }

    /**
     * Removes a student from a classroom.
     *
     * @param classroomId classroom
     * @param accountId   student to remove
     * @return no content
     */
    @DeleteMapping(EducationalRoutes.CLASSROOMS + "/{classroomId}/students/{accountId}")
    @Operation(summary = "Removes a student from a classroom",
            description = "Ends the enrollment. Nothing is deleted, and the teacher's access to "
                    + "that student stops at the next request.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Student removed"),
            @ApiResponse(responseCode = "404", description = "No such classroom for this teacher, "
                    + "or the student is not in it",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))})
    public ResponseEntity<Void> removeStudent(@PathVariable UUID classroomId,
            @PathVariable UUID accountId) {
        classrooms.remove(CurrentAccount.require().accountId(), classroomId, accountId);
        return ResponseEntity.noContent().build();
    }

    private static ClassroomResponse responseFor(ClassroomView classroom, int studentCount) {
        return new ClassroomResponse(classroom.id(), classroom.name(), classroom.status(),
                classroom.subjectIds(), studentCount, classroom.createdAt(), classroom.archivedAt());
    }

    private static EnrollmentResponse responseFor(EnrollmentView enrollment, String classroomName) {
        return new EnrollmentResponse(enrollment.id(), enrollment.classroomId(), classroomName,
                enrollment.accountId(), enrollment.enrolledAt(), enrollment.endedAt(),
                enrollment.endedReason());
    }
}
