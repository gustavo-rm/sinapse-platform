package br.com.sinapse.platform.readmodel.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.sinapse.platform.curriculum.api.CurriculumCatalog;
import br.com.sinapse.platform.curriculum.api.SubjectView;
import br.com.sinapse.platform.curriculum.api.TopicView;
import br.com.sinapse.platform.educational.api.ClassroomView;
import br.com.sinapse.platform.identity.api.AccountAccessPolicy;
import br.com.sinapse.platform.identity.api.ConsentPurpose;
import br.com.sinapse.platform.identity.internal.domain.Account;
import br.com.sinapse.platform.planning.api.PlannedSessionView;
import br.com.sinapse.platform.planning.api.StudyPlanView;
import br.com.sinapse.platform.readmodel.support.ReadModelIntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The batch lookups the read models required each module to publish.
 *
 * <p>Section 4 of the API contract makes these part of each module's contract rather than a
 * detail of whoever composes above it: a module that offers only unit lookups pushes the N+1
 * onto every one of its consumers. Each one here is asserted to answer exactly what the unit
 * method would answer for the same input, because a batch that quietly disagrees with the unit
 * form is worse than no batch at all.
 */
class BatchLookupIntegrationTest extends ReadModelIntegrationTest {

    @Autowired
    private CurriculumCatalog catalog;

    @Autowired
    private AccountAccessPolicy accounts;

    @Test
    void subjectsComeBackKeyedByIdentifierAndUnknownOnesAreSimplyAbsent() {
        SubjectView first = subject();
        SubjectView second = subject();
        UUID unknown = UUID.randomUUID();

        Map<UUID, SubjectView> found =
                catalog.subjectsByIds(List.of(first.id(), second.id(), unknown));

        assertThat(found).containsOnlyKeys(first.id(), second.id());
        assertThat(found.get(first.id()).name()).isEqualTo(first.name());
        assertThat(catalog.subjectsByIds(List.of())).isEmpty();
    }

    @Test
    void theStateOfAnAccountIsOneAnswerAndAnUnknownAccountIsEmpty() {
        Account student = student();

        assertThat(accountDirectory.stateOf(student.id())).hasValueSatisfying(state -> {
            assertThat(state.accountId()).isEqualTo(student.id());
            assertThat(state.timeZone()).isEqualTo(student.timeZone());
        });
        assertThat(accountDirectory.stateOf(UUID.randomUUID())).isEmpty();
    }

    /** The batch and the unit form of the sharing gate must never disagree. */
    @Test
    void theBatchSharingGateAgreesWithTheUnitOne() {
        Account sharing = student();
        Account notSharing = student();
        consents.grant(sharing.id(), ConsentPurpose.INSTITUTION_SHARING,
                identity.currentTermsId(ConsentPurpose.INSTITUTION_SHARING), identity.evidence());
        List<UUID> asked = List.of(sharing.id(), notSharing.id(), UUID.randomUUID());

        Set<UUID> allowed = accounts.canShareWithInstitution(asked);

        assertThat(allowed).containsExactly(sharing.id());
        assertThat(asked).allSatisfy(accountId ->
                assertThat(allowed.contains(accountId))
                        .isEqualTo(accounts.canShareWithInstitution(accountId)));
        assertThat(accounts.canShareWithInstitution(List.of())).isEmpty();
    }

    @Test
    void theBatchTeacherGateAgreesWithTheUnitOne() {
        Account teacher = teacherAccount();
        ClassroomView classroom = classroomOf(teacher);
        Account inside = student();
        Account outside = student();
        enrol(inside, teacher, classroom);
        List<UUID> asked = List.of(inside.id(), outside.id());

        Set<UUID> viewable = teacherAccess.viewableStudents(teacher.id(), asked);

        assertThat(viewable).containsExactly(inside.id());
        assertThat(asked).allSatisfy(accountId ->
                assertThat(viewable.contains(accountId))
                        .isEqualTo(teacherAccess.canViewStudent(teacher.id(), accountId)));

        revokeSharing(inside);
        assertThat(teacherAccess.viewableStudents(teacher.id(), asked))
                .as("read at the moment of the call, like the unit form")
                .isEmpty();
    }

    @Test
    void plannedSessionsComeBackGroupedByTheStudentTheyBelongTo() {
        Account first = studentReadyToPlan(3);
        Account second = studentReadyToPlan(2);
        Account withoutAPlan = student();
        StudyPlanView firstPlan = planFor(first);
        StudyPlanView secondPlan = planFor(second);

        Map<UUID, List<PlannedSessionView>> byStudent = directory.plannedSessionsOfAccounts(
                List.of(first.id(), second.id(), withoutAPlan.id()), windowStart(), windowEnd());

        assertThat(byStudent).containsOnlyKeys(first.id(), second.id());
        assertThat(byStudent.get(first.id()))
                .containsExactlyElementsOf(directory.plannedSessionsOf(first.id(),
                        windowStart(), windowEnd()));
        assertThat(byStudent.get(second.id())).allSatisfy(session ->
                assertThat(session.planId()).isEqualTo(secondPlan.id()));
        assertThat(firstPlan.id()).isNotEqualTo(secondPlan.id());
        assertThat(directory.plannedSessionsOfAccounts(List.of(), windowStart(), windowEnd()))
                .isEmpty();
    }

    @Test
    void aClassroomIsAnsweredOnlyToTheTeacherWhoOwnsIt() {
        Account owner = teacherAccount();
        Account other = teacherAccount();
        ClassroomView classroom = classroomOf(owner);

        assertThat(educational.classroomOwnedBy(owner.id(), classroom.id()))
                .hasValueSatisfying(found -> assertThat(found.id()).isEqualTo(classroom.id()));
        assertThat(educational.classroomOwnedBy(other.id(), classroom.id())).isEmpty();
        assertThat(educational.classroomOwnedBy(owner.id(), UUID.randomUUID())).isEmpty();
    }

    /**
     * Readiness is one answer, and the generation job asks the same one.
     *
     * <p>Two definitions of "enough to plan" would show up as a screen that offers the button
     * and a job that refuses it, which is the worst version of both.
     */
    @Test
    void readinessAndTheUnfinishedJobAreAnsweredByTheModuleThatOwnsThem() {
        Account student = student();
        assertThat(directory.isReadyToPlan(student.id())).isFalse();
        assertThat(directory.unfinishedGenerationRequestIdOf(student.id())).isEmpty();

        declareWeekdayEvenings(student);
        goalWithTopics(student, 2);

        assertThat(directory.isReadyToPlan(student.id())).isTrue();
        UUID queued = requestService.queue(student.id(), null).id();
        assertThat(directory.unfinishedGenerationRequestIdOf(student.id())).contains(queued);

        worker.runOnce();
        assertThat(directory.unfinishedGenerationRequestIdOf(student.id()))
                .as("a finished job is not one the screen should still be waiting on")
                .isEmpty();
    }

    @Test
    void topicsStillComeBackInCurricularOrderWhenAskedForInBulk() {
        SubjectView subject = subject();
        TopicView first = topicOf(subject);
        TopicView second = topicOf(subject);

        List<TopicView> topics = catalog.topicsByIds(List.of(second.id(), first.id()));

        assertThat(topics).extracting(TopicView::id).containsExactly(first.id(), second.id());
    }
}
