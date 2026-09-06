package br.com.sinapse.platform.educational.internal.web;

import br.com.sinapse.platform.educational.api.ClassroomView;
import br.com.sinapse.platform.educational.internal.domain.Enrollment;
import br.com.sinapse.platform.educational.internal.persistence.EnrollmentRepository;
import br.com.sinapse.platform.educational.internal.service.ClassroomService;
import br.com.sinapse.platform.educational.internal.service.EducationalDirectoryService;
import br.com.sinapse.platform.identity.api.CurrentAccount;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * The student's own side: which classrooms they are in, and leaving one.
 */
@RestController
@Tag(name = "Enrollments", description = "The caller's own memberships of classrooms")
public class EnrollmentController {

    private final EnrollmentRepository enrollments;
    private final EducationalDirectoryService directory;
    private final ClassroomService classrooms;

    /**
     * @param enrollments enrollments
     * @param directory   reads of classrooms
     * @param classrooms  classroom use cases
     */
    public EnrollmentController(EnrollmentRepository enrollments,
            EducationalDirectoryService directory, ClassroomService classrooms) {
        this.enrollments = enrollments;
        this.directory = directory;
        this.classrooms = classrooms;
    }

    /**
     * The caller's memberships, newest first, ended ones included.
     *
     * <p>Ended ones are included on purpose. A student is entitled to see which classrooms have
     * had access to their data and when that stopped, and a list of only the current ones would
     * be a status display rather than a record.
     *
     * @return the memberships
     */
    @GetMapping(value = EducationalRoutes.ENROLLMENTS, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Lists the caller's own memberships of classrooms",
            description = "Ended memberships are included: they are the record of which teachers "
                    + "had access to this student's data, and when it stopped.")
    @ApiResponse(responseCode = "200", description = "The memberships")
    @Transactional(readOnly = true)
    public List<EnrollmentResponse> list() {
        UUID caller = CurrentAccount.require().accountId();
        List<Enrollment> own = enrollments.findByAccountIdOrderByEnrolledAtDesc(caller);

        Map<UUID, String> names = own.stream()
                .map(Enrollment::classroomId)
                .distinct()
                .map(directory::classroom)
                .flatMap(Optional::stream)
                .collect(Collectors.toMap(ClassroomView::id, ClassroomView::name));

        return own.stream()
                .map(enrollment -> new EnrollmentResponse(enrollment.id(), enrollment.classroomId(),
                        names.get(enrollment.classroomId()), enrollment.accountId(),
                        enrollment.enrolledAt(), enrollment.endedAt(), enrollment.endedReason()))
                .toList();
    }

    /**
     * Leaves a classroom.
     *
     * @param enrollmentId membership to end, which must be the caller's
     * @return no content
     */
    @DeleteMapping(EducationalRoutes.ENROLLMENTS + "/{enrollmentId}")
    @Operation(summary = "Leaves a classroom",
            description = "Ends the membership. Nothing is deleted: the record is what justifies "
                    + "the access the teacher had while it was active.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Left the classroom"),
            @ApiResponse(responseCode = "404", description = "No such active membership for this "
                    + "caller",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))})
    public ResponseEntity<Void> leave(@PathVariable UUID enrollmentId) {
        classrooms.leave(CurrentAccount.require().accountId(), enrollmentId);
        return ResponseEntity.noContent().build();
    }
}
