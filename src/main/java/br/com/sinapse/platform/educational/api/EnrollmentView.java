package br.com.sinapse.platform.educational.api;

import java.time.Instant;
import java.util.UUID;

/**
 * An enrollment, as other modules and clients see it.
 *
 * @param id          identifier
 * @param classroomId classroom the student is in
 * @param accountId   student
 * @param enrolledAt  when it started
 * @param endedAt     when it ended, or {@code null} while active
 * @param endedReason why it ended, or {@code null} while active
 */
public record EnrollmentView(
        UUID id,
        UUID classroomId,
        UUID accountId,
        Instant enrolledAt,
        Instant endedAt,
        EnrollmentEndReason endedReason) {

    /** Whether the enrollment is currently active. */
    public boolean isActive() {
        return endedAt == null;
    }
}
