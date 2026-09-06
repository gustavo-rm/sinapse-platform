package br.com.sinapse.platform.planning.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.sinapse.platform.planning.api.GoalStatus;
import br.com.sinapse.platform.planning.internal.domain.StudyGoal;
import br.com.sinapse.platform.planning.internal.error.GoalAlreadyClosedException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The rules a goal holds on its own, without a database in the way. */
class StudyGoalTest {

    private static final Instant NOW = Instant.parse("2026-09-06T12:00:00Z");
    private static final LocalDate DECEMBER = LocalDate.parse("2026-12-01");

    @Test
    void aGoalIsBornActiveAndOpen() {
        StudyGoal goal = goal();

        assertThat(goal.status()).isEqualTo(GoalStatus.ACTIVE);
        assertThat(goal.isActive()).isTrue();
        assertThat(goal.achievedAt()).isNull();
        assertThat(goal.priority()).isEqualTo(3);
    }

    @Test
    void revisingChangesTheDateAndThePriorityAndNothingElse() {
        StudyGoal goal = goal();
        UUID subject = goal.subjectId();

        goal.revise(DECEMBER, 5);

        assertThat(goal.targetDate()).isEqualTo(DECEMBER);
        assertThat(goal.priority()).isEqualTo(5);
        assertThat(goal.subjectId())
                .as("a goal about another subject is another goal; repointing one would move "
                        + "the record of an abandoned subject onto a new one")
                .isEqualTo(subject);
        assertThat(goal.createdAt()).isEqualTo(NOW);
    }

    @Test
    void revisingCanDropTheTargetDate() {
        StudyGoal goal = goal();
        goal.revise(DECEMBER, 4);

        goal.revise(null, 4);

        assertThat(goal.targetDate()).isNull();
    }

    @Test
    void aGoalClosesOnceAndRecordsWhen() {
        StudyGoal achieved = goal();
        StudyGoal abandoned = goal();

        achieved.achieve(NOW.plusSeconds(3600));
        abandoned.abandon(NOW.plusSeconds(3600));

        assertThat(achieved.status()).isEqualTo(GoalStatus.ACHIEVED);
        assertThat(abandoned.status()).isEqualTo(GoalStatus.ABANDONED);
        assertThat(abandoned.achievedAt())
                .as("what a student gave up on, and when, is the more interesting half of the "
                        + "data; the column is named for the happy case and records both")
                .isEqualTo(NOW.plusSeconds(3600));
    }

    @Test
    void aClosedGoalIsNotRevisedOrClosedAgain() {
        StudyGoal goal = goal();
        goal.abandon(NOW.plusSeconds(60));

        assertThatThrownBy(() -> goal.revise(DECEMBER, 5))
                .as("reopening a closed goal by revising it would lose the instant the student "
                        + "stopped pursuing it")
                .isInstanceOf(GoalAlreadyClosedException.class);
        assertThatThrownBy(() -> goal.achieve(NOW.plusSeconds(120)))
                .isInstanceOf(GoalAlreadyClosedException.class);
        assertThatThrownBy(() -> goal.abandon(NOW.plusSeconds(120)))
                .isInstanceOf(GoalAlreadyClosedException.class);

        assertThat(goal.achievedAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(goal.status()).isEqualTo(GoalStatus.ABANDONED);
    }

    private static StudyGoal goal() {
        return new StudyGoal(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, 3, NOW);
    }
}
