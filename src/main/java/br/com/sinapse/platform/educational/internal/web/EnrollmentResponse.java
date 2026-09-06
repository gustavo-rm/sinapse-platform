package br.com.sinapse.platform.educational.internal.web;

import br.com.sinapse.platform.educational.api.EnrollmentEndReason;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * A membership of a classroom.
 *
 * @param id            identifier
 * @param classroomId   classroom
 * @param classroomName its name, so a list of memberships is readable without a second call
 * @param accountId     student
 * @param enrolledAt    when it started
 * @param endedAt       when it ended, or {@code null}
 * @param endedReason   why it ended, or {@code null}
 */
@Schema(description = "A student's membership of a classroom")
public record EnrollmentResponse(
        UUID id,
        UUID classroomId,
        String classroomName,
        UUID accountId,
        Instant enrolledAt,
        Instant endedAt,
        EnrollmentEndReason endedReason) {
}
