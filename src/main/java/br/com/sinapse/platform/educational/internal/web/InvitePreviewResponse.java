package br.com.sinapse.platform.educational.internal.web;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * What a student is shown before accepting an invite.
 *
 * <p>{@code visibility} is the field this endpoint exists for. The scope adopted is integral:
 * the teacher will see the student's whole history and plan, not only the part belonging to
 * this classroom's subjects. ADR 0005 accepted that cost in data minimisation on one
 * condition — that the student is told, at the moment of deciding, rather than in terms of use
 * nobody reads. Without this field the consent is not informed and the whole arrangement does
 * not hold.
 *
 * @param classroomId     classroom the code admits to
 * @param classroomName   its name
 * @param teacherName     the teacher who will gain access
 * @param institutionName their institution, or {@code null}
 * @param subjectNames    subjects that carry an institutional deadline here
 * @param visibility      what the teacher will be able to see
 * @param requiresConsent the consent purpose the student has to have granted
 * @param expiresAt       when the code stops working
 */
@Schema(description = "What accepting an invite discloses, shown before the student accepts")
public record InvitePreviewResponse(
        UUID classroomId,
        String classroomName,
        String teacherName,
        String institutionName,
        List<String> subjectNames,
        VisibilityDescription visibility,
        String requiresConsent,
        Instant expiresAt) {

    /**
     * The scope, said twice: once for a machine and once for a person.
     *
     * @param scope       the scope name, which a client may switch on
     * @param subjectIds  subjects the scope is limited to, empty when it is not limited
     * @param description a sentence a student can read. English, like every other text this
     *                    API returns; translation is the client's job
     */
    @Schema(description = "What the teacher will be able to see")
    public record VisibilityDescription(String scope, List<UUID> subjectIds, String description) {
    }
}
