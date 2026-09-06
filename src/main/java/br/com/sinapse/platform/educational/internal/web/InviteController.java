package br.com.sinapse.platform.educational.internal.web;

import br.com.sinapse.platform.educational.api.EnrollmentView;
import br.com.sinapse.platform.educational.api.VisibilityScope;
import br.com.sinapse.platform.educational.internal.domain.Invite;
import br.com.sinapse.platform.educational.internal.service.InviteService;
import br.com.sinapse.platform.identity.api.CurrentAccount;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Clock;
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
 * Invites: issuing and revoking them as a teacher, previewing and redeeming them as a student.
 *
 * <p>The preview and the redemption are rate limited twice over, per origin and per account.
 * That is not belt and braces: the code is stored in clear text, which is what lets a teacher
 * read it out again, and the entire compensation for that is that nobody may guess without
 * limit. The limits live in configuration and are applied in the filter chain, so a burst of
 * invalid codes is refused before any of this runs.
 */
@RestController
@Tag(name = "Invites", description = "Joining a classroom by code")
public class InviteController {

    /** The purpose a student must have consented to before redeeming. */
    private static final String REQUIRED_CONSENT = "INSTITUTION_SHARING";

    /** What the integral scope means, in a sentence a person can read. */
    private static final String INTEGRAL_SCOPE_DESCRIPTION =
            "The teacher will be able to see your whole study history and study plan, including "
                    + "subjects that are not part of this classroom.";

    private final InviteService invites;
    private final Clock clock;

    /**
     * @param invites invite use cases
     * @param clock   application clock, read to say whether an invite is still good
     */
    public InviteController(InviteService invites, Clock clock) {
        this.invites = invites;
        this.clock = clock;
    }

    /**
     * Issues an invite for a classroom.
     *
     * @param classroomId classroom
     * @param request     lifetime and use limit, both optional
     * @return the invite, with its code
     */
    @PostMapping(value = EducationalRoutes.CLASSROOMS + "/{classroomId}/invites",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Issues an invite for a classroom",
            description = "Ten characters of Crockford base32, about fifty bits. Expiry is "
                    + "mandatory; omitting the lifetime uses the configured default rather than "
                    + "making the code permanent.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Invite issued"),
            @ApiResponse(responseCode = "400", description = "The requested lifetime is out of range",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "No such classroom for this teacher",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))})
    public ResponseEntity<InviteResponse> issue(@PathVariable UUID classroomId,
            @Valid @RequestBody(required = false) InviteRequest request) {

        InviteRequest submitted = request == null ? new InviteRequest(null, null) : request;
        Invite invite = invites.issue(CurrentAccount.require().accountId(), classroomId,
                submitted.lifetime(), submitted.maxUses());
        return ResponseEntity.status(HttpStatus.CREATED).body(responseFor(invite));
    }

    /**
     * The invites of a classroom.
     *
     * @param classroomId classroom
     * @return its invites, newest first, spent and revoked ones included
     */
    @GetMapping(value = EducationalRoutes.CLASSROOMS + "/{classroomId}/invites",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Lists the invites of a classroom",
            description = "Codes are returned in clear. That is why they are stored in clear, and "
                    + "why this route is restricted to the classroom's own teacher.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The invites"),
            @ApiResponse(responseCode = "404", description = "No such classroom for this teacher",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))})
    public List<InviteResponse> list(@PathVariable UUID classroomId) {
        return invites.listFor(CurrentAccount.require().accountId(), classroomId).stream()
                .map(this::responseFor)
                .toList();
    }

    /**
     * Revokes an invite.
     *
     * @param inviteId invite to revoke
     * @return no content
     */
    @DeleteMapping(EducationalRoutes.INVITES + "/{inviteId}")
    @Operation(summary = "Revokes an invite")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Invite revoked"),
            @ApiResponse(responseCode = "404", description = "No such invite for this teacher",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))})
    public ResponseEntity<Void> revoke(@PathVariable UUID inviteId) {
        invites.revoke(CurrentAccount.require().accountId(), inviteId);
        return ResponseEntity.noContent().build();
    }

    /**
     * What accepting this code would disclose.
     *
     * @param code code the student is holding
     * @return the classroom, the teacher, and what the teacher will be able to see
     */
    @GetMapping(value = EducationalRoutes.INVITES + "/{code}/preview",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Shows what accepting an invite would disclose",
            description = "A requirement of ADR 0005 rather than a courtesy. The visibility scope "
                    + "is integral — the teacher sees the student's whole history and plan — and a "
                    + "consent to that which is not shown at the moment of deciding is not "
                    + "informed consent.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "What the code admits to"),
            @ApiResponse(responseCode = "404", description = "The code admits to nothing: unknown, "
                    + "expired, revoked, exhausted, or its classroom is archived. The five answer "
                    + "identically on purpose",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))})
    public InvitePreviewResponse preview(@PathVariable String code) {
        InviteService.InvitePreview preview = invites.preview(code);
        return new InvitePreviewResponse(
                preview.classroomId(),
                preview.classroomName(),
                preview.teacherName(),
                preview.institutionName(),
                preview.subjectNames(),
                describe(VisibilityScope.ALL),
                REQUIRED_CONSENT,
                preview.expiresAt());
    }

    /**
     * Redeems a code.
     *
     * <p>The sharing consent is not granted here. The student grants it through the identity
     * module, after reading the preview, and this route checks that it holds — which keeps the
     * one place that writes a consent record in the module that owns consent.
     *
     * @param code code the student is holding
     * @return the enrollment
     */
    @PostMapping(value = EducationalRoutes.INVITES + "/{code}/redemptions",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Redeems an invite and joins the classroom",
            description = "Requires an active account with a valid INSTITUTION_SHARING consent, "
                    + "granted separately after reading the preview. A teacher cannot redeem an "
                    + "invite to their own classroom.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Enrolled"),
            @ApiResponse(responseCode = "403", description = "The account may not share its data "
                    + "with an institution",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "The code admits to nothing",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Already enrolled, or the caller owns "
                    + "the classroom",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))})
    public ResponseEntity<EnrollmentResponse> redeem(@PathVariable String code) {
        EnrollmentView enrollment = invites.redeem(CurrentAccount.require().accountId(), code);
        return ResponseEntity.status(HttpStatus.CREATED).body(new EnrollmentResponse(
                enrollment.id(), enrollment.classroomId(), null, enrollment.accountId(),
                enrollment.enrolledAt(), enrollment.endedAt(), enrollment.endedReason()));
    }

    /**
     * Renders a scope for a person and for a machine at once.
     *
     * <p>The switch is exhaustive over a sealed type, so adding a scope makes this stop
     * compiling rather than silently describing the new one as the old one. That is the point
     * of the type being sealed.
     *
     * <p>Package-private rather than private so that the branch nothing produces yet can still
     * be exercised. It is the reversal point of decision P1, and a reversal point that has
     * never been run is a claim rather than a mechanism.
     */
    static InvitePreviewResponse.VisibilityDescription describe(VisibilityScope scope) {
        return switch (scope) {
            case VisibilityScope.All ignored ->
                    new InvitePreviewResponse.VisibilityDescription("ALL", List.of(),
                            INTEGRAL_SCOPE_DESCRIPTION);
            case VisibilityScope.Subjects subjects ->
                    new InvitePreviewResponse.VisibilityDescription("SUBJECTS",
                            List.copyOf(subjects.subjectIds()),
                            "The teacher will be able to see your study history and plan for the "
                                    + "subjects of this classroom only.");
        };
    }

    private InviteResponse responseFor(Invite invite) {
        return new InviteResponse(invite.id(), invite.code(), invite.expiresAt(), invite.maxUses(),
                invite.useCount(), invite.revokedAt(), invite.isRedeemableAt(clock.instant()));
    }
}
