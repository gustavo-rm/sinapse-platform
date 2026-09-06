package br.com.sinapse.platform.planning.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.sinapse.platform.curriculum.api.SubjectView;
import br.com.sinapse.platform.identity.api.ConsentPurpose;
import br.com.sinapse.platform.identity.internal.domain.Account;
import br.com.sinapse.platform.identity.internal.service.ConsentService;
import br.com.sinapse.platform.planning.api.GoalStatus;
import br.com.sinapse.platform.planning.api.StudyGoalView;
import br.com.sinapse.platform.planning.internal.error.DuplicateGoalException;
import br.com.sinapse.platform.planning.internal.error.GoalAlreadyClosedException;
import br.com.sinapse.platform.planning.internal.error.PlanningDataNotProcessableException;
import br.com.sinapse.platform.planning.internal.error.UnknownGoalException;
import br.com.sinapse.platform.planning.internal.error.UnknownSubjectException;
import br.com.sinapse.platform.planning.support.PlanningIntegrationTest;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Setting, revising and closing goals, through the service that owns them. */
class GoalIntegrationTest extends PlanningIntegrationTest {

    private static final LocalDate DECEMBER = LocalDate.parse("2026-12-01");

    @Autowired
    private ConsentService consents;

    @Test
    void aStudentSetsAGoalForASubject() {
        Account student = student();
        SubjectView subject = subject();

        StudyGoalView goal = goals.set(student.id(), subject.id(), DECEMBER, 5);

        assertThat(goal.status()).isEqualTo(GoalStatus.ACTIVE);
        assertThat(goal.subjectId())
                .as("a goal names a subject and never a topic: every topic of it is in scope, "
                        + "and the core decides the order and what fits (decision L3)")
                .isEqualTo(subject.id());
        assertThat(goal.targetDate()).isEqualTo(DECEMBER);
        assertThat(goal.priority()).isEqualTo(5);
        assertThat(directory.activeGoalsOf(student.id())).hasSize(1);
    }

    @Test
    void aSecondActiveGoalForTheSameSubjectIsRefused() {
        Account student = student();
        SubjectView subject = subject();
        goals.set(student.id(), subject.id(), null, 3);

        assertThatThrownBy(() -> goals.set(student.id(), subject.id(), DECEMBER, 5))
                .as("two active goals for one subject would put the same topics into the "
                        + "snapshot twice with two priorities, and the core would have no way to "
                        + "decide which the student meant")
                .isInstanceOf(DuplicateGoalException.class);

        assertThat(directory.goalsOf(student.id())).hasSize(1);
    }

    @Test
    void theSubjectIsFreeAgainOnceTheGoalIsClosed() {
        Account student = student();
        SubjectView subject = subject();
        StudyGoalView first = goals.set(student.id(), subject.id(), null, 3);

        goals.abandon(student.id(), first.id());

        assertThatCode(() -> goals.set(student.id(), subject.id(), DECEMBER, 4))
                .doesNotThrowAnyException();
        assertThat(directory.goalsOf(student.id()))
                .as("the abandoned goal stays on record; what a student gave up on is data "
                        + "about what people actually pursue")
                .hasSize(2);
        assertThat(directory.activeGoalsOf(student.id())).hasSize(1);
    }

    @Test
    void twoStudentsMayPursueTheSameSubject() {
        SubjectView subject = subject();
        goals.set(student().id(), subject.id(), null, 3);

        assertThatCode(() -> goals.set(student().id(), subject.id(), null, 3))
                .doesNotThrowAnyException();
    }

    @Test
    void revisingChangesWhatTheGoalAsksForAndNotWhichSubject() {
        Account student = student();
        SubjectView subject = subject();
        StudyGoalView goal = goals.set(student.id(), subject.id(), null, 3);

        StudyGoalView revised = goals.revise(student.id(), goal.id(), DECEMBER, 1);

        assertThat(revised.targetDate()).isEqualTo(DECEMBER);
        assertThat(revised.priority()).isEqualTo(1);
        assertThat(revised.subjectId()).isEqualTo(subject.id());
        assertThat(revised.createdAt()).isEqualTo(goal.createdAt());
    }

    @Test
    void aClosedGoalIsNotRevisedAgain() {
        Account student = student();
        StudyGoalView goal = goals.set(student.id(), subject().id(), null, 3);
        goals.achieve(student.id(), goal.id());

        assertThatThrownBy(() -> goals.revise(student.id(), goal.id(), DECEMBER, 5))
                .isInstanceOf(GoalAlreadyClosedException.class);
        assertThatThrownBy(() -> goals.abandon(student.id(), goal.id()))
                .isInstanceOf(GoalAlreadyClosedException.class);

        assertThat(directory.goalsOf(student.id()))
                .singleElement()
                .satisfies(closed -> {
                    assertThat(closed.status()).isEqualTo(GoalStatus.ACHIEVED);
                    assertThat(closed.achievedAt()).isNotNull();
                });
    }

    @Test
    void aSubjectOutsideTheCatalogueIsRefused() {
        Account student = student();

        assertThatThrownBy(() -> goals.set(student.id(), UUID.randomUUID(), null, 3))
                .isInstanceOf(UnknownSubjectException.class);
    }

    @Test
    void aGoalOfAnotherAccountIsNotReachable() {
        Account owner = student();
        Account other = student();
        StudyGoalView goal = goals.set(owner.id(), subject().id(), null, 3);

        assertThatThrownBy(() -> goals.revise(other.id(), goal.id(), DECEMBER, 5))
                .as("a goal that exists and one belonging to somebody else answer the same way")
                .isInstanceOf(UnknownGoalException.class);
        assertThatThrownBy(() -> goals.achieve(other.id(), goal.id()))
                .isInstanceOf(UnknownGoalException.class);
        assertThatThrownBy(() -> goals.abandon(other.id(), goal.id()))
                .isInstanceOf(UnknownGoalException.class);
    }

    @Test
    void goalsComeBackMostPressingFirst() {
        Account student = student();
        goals.set(student.id(), subject().id(), null, 2);
        goals.set(student.id(), subject().id(), null, 5);
        goals.set(student.id(), subject().id(), null, 3);

        assertThat(directory.activeGoalsOf(student.id()))
                .extracting(StudyGoalView::priority)
                .containsExactly(5, 3, 2);
    }

    @Test
    void anAccountWhoseDataMayNotBeProcessedSetsNothing() {
        Account student = student();
        SubjectView subject = subject();
        StudyGoalView goal = goals.set(student.id(), subject.id(), null, 3);

        consents.revoke(student.id(), ConsentPurpose.LEARNING_DATA_PROCESSING);

        assertThatThrownBy(() -> goals.set(student.id(), subject().id(), null, 3))
                .isInstanceOf(PlanningDataNotProcessableException.class);
        assertThatThrownBy(() -> goals.revise(student.id(), goal.id(), DECEMBER, 5))
                .isInstanceOf(PlanningDataNotProcessableException.class);
        assertThatThrownBy(() -> goals.achieve(student.id(), goal.id()))
                .isInstanceOf(PlanningDataNotProcessableException.class);
    }
}
