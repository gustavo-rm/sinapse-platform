package br.com.sinapse.platform.educational.api;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * A classroom, as other modules and clients see it.
 *
 * <p>{@code subjectIds} does <em>not</em> say what the teacher can see. It declares which
 * subjects carry an institutional deadline, which is input for the optimisation core. What
 * the teacher can see is {@link VisibilityScope}, and it is decided elsewhere on purpose:
 * conflating the two would make a curriculum choice into an authorisation one.
 *
 * @param id         identifier
 * @param teacherId  teacher who owns it
 * @param name       display name
 * @param status     whether it is still running
 * @param subjectIds subjects that carry an institutional deadline here
 * @param createdAt  when it was created
 * @param archivedAt when it was archived, or {@code null}
 */
public record ClassroomView(
        UUID id,
        UUID teacherId,
        String name,
        ClassroomStatus status,
        Set<UUID> subjectIds,
        Instant createdAt,
        Instant archivedAt) {

    /** Copies the set so a view cannot be changed after it was produced. */
    public ClassroomView {
        subjectIds = Set.copyOf(subjectIds);
    }
}
