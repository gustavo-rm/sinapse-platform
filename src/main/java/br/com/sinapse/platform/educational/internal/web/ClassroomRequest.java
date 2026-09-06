package br.com.sinapse.platform.educational.internal.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;

/**
 * What opening a classroom submits.
 *
 * @param name       display name students will see
 * @param subjectIds subjects that carry an institutional deadline here. Not a statement about
 *                   what the teacher will be able to see
 */
@Schema(description = "A classroom to open")
public record ClassroomRequest(
        @NotBlank @Size(max = 200) String name,
        Set<UUID> subjectIds) {

    /** Treats an omitted set as an empty one, so a classroom with no deadlines is expressible. */
    public ClassroomRequest {
        subjectIds = subjectIds == null ? Set.of() : Set.copyOf(subjectIds);
    }
}
