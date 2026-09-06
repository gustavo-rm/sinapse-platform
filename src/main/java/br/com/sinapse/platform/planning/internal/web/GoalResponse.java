package br.com.sinapse.platform.planning.internal.web;

import br.com.sinapse.platform.planning.api.GoalStatus;
import br.com.sinapse.platform.planning.api.StudyGoalView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A goal, as the API returns it.
 *
 * @param id         identifier
 * @param subjectId  subject in scope, with every topic of it
 * @param targetDate when the student would like to be done, or {@code null}
 * @param priority   1 to 5, higher meaning more pressing
 * @param status     where the goal stands
 * @param createdAt  when it was set
 * @param closedAt   when it stopped being pursued, or {@code null} while active
 */
@Schema(description = "A subject the student intends to get through")
public record GoalResponse(
        UUID id,
        UUID subjectId,
        LocalDate targetDate,
        int priority,
        GoalStatus status,
        Instant createdAt,
        Instant closedAt) {

    /**
     * @param view goal to render
     * @return the response body
     */
    static GoalResponse of(StudyGoalView view) {
        return new GoalResponse(view.id(), view.subjectId(), view.targetDate(), view.priority(),
                view.status(), view.createdAt(), view.achievedAt());
    }
}
