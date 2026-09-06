package br.com.sinapse.platform.educational.internal.web;

import br.com.sinapse.platform.educational.api.ClassroomStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * A classroom as its own teacher sees it.
 *
 * @param id           identifier
 * @param name         display name
 * @param status       whether it is still running
 * @param subjectIds   subjects that carry an institutional deadline here
 * @param studentCount how many students are currently in it
 * @param createdAt    when it was opened
 * @param archivedAt   when it was archived, or {@code null}
 */
@Schema(description = "A classroom")
public record ClassroomResponse(
        UUID id,
        String name,
        ClassroomStatus status,
        Set<UUID> subjectIds,
        int studentCount,
        Instant createdAt,
        Instant archivedAt) {
}
