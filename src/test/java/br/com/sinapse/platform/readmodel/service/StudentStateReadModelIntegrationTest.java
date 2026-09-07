package br.com.sinapse.platform.readmodel.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.sinapse.platform.identity.api.AccountStatus;
import br.com.sinapse.platform.identity.api.ConsentPurpose;
import br.com.sinapse.platform.identity.internal.domain.Account;
import br.com.sinapse.platform.identity.support.IdentityFixtures;
import br.com.sinapse.platform.learningrecord.api.StudySessionView;
import br.com.sinapse.platform.planning.api.StudyPlanView;
import br.com.sinapse.platform.readmodel.api.StudentStateView;
import br.com.sinapse.platform.readmodel.support.ReadModelIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** {@code EstadoDoAluno}: what the initial screen is told, and what it is not told. */
class StudentStateReadModelIntegrationTest extends ReadModelIntegrationTest {

    @Test
    void aFreshAccountHasNothingToResumeAndNothingPlanned() {
        Account student = student();

        StudentStateView state = states.of(student.id());

        assertThat(state.accountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(state.timeZone()).isEqualTo(IdentityFixtures.DEFAULT_ZONE);
        assertThat(state.setupComplete()).isFalse();
        assertThat(state.activePlanId()).isNull();
        assertThat(state.activeGenerationJobId()).isNull();
        assertThat(state.openSessionId()).isNull();
        assertThat(state.requiresMajorityReaffirmation()).isFalse();
    }

    /**
     * The purpose granted in the course of joining a classroom is never listed as pending.
     *
     * <p>ADR 0004 has {@code INSTITUTION_SHARING} consented when an invite is redeemed and never
     * at registration, because consenting up front to something the holder may never do is not
     * informed. A screen that listed it as outstanding would be an invitation to ask anyway,
     * which is what this asserts cannot happen.
     */
    @Test
    void onlyThePurposesRaisedUnpromptedAreReportedAsPending() {
        Account student = identity.activeAdult(identity.uniqueEmail(), false);

        StudentStateView state = states.of(student.id());

        assertThat(state.pendingConsents()).containsExactly(ConsentPurpose.ACADEMIC_RESEARCH);
    }

    /** A purpose the holder decided is not raised again, granted or withdrawn. */
    @Test
    void aWithdrawnPurposeIsNotOfferedAgainAsPending() {
        Account student = identity.activeAdult(identity.uniqueEmail(), true);
        assertThat(states.of(student.id()).pendingConsents()).isEmpty();

        consents.revoke(student.id(), ConsentPurpose.ACADEMIC_RESEARCH);

        assertThat(states.of(student.id()).pendingConsents())
                .as("a consent that has to be asked for repeatedly is not freely given")
                .isEmpty();
    }

    @Test
    void readinessAgreesWithWhatTheGenerationJobRequires() {
        Account student = student();
        assertThat(states.of(student.id()).setupComplete()).isFalse();

        declareWeekdayEvenings(student);
        assertThat(states.of(student.id()).setupComplete())
                .as("availability alone is not enough: decision F2 wants a goal too")
                .isFalse();

        goalWithTopics(student, 2);
        assertThat(states.of(student.id()).setupComplete()).isTrue();
    }

    @Test
    void thePlanInForceAndTheSessionLeftOpenAreBothReported() {
        Account student = studentReadyToPlan(3);
        StudyPlanView plan = planFor(student);
        UUID topicId = directory.sessionsOfPlan(plan.id()).getFirst().topicId();
        StudySessionView open = leftOpen(student, topicId);

        StudentStateView state = states.of(student.id());

        assertThat(state.activePlanId()).isEqualTo(plan.id());
        assertThat(state.openSessionId()).isEqualTo(open.id());
        assertThat(state.activeGenerationJobId())
                .as("the job that produced the plan has finished")
                .isNull();
    }

    /**
     * A holder whose data may not be processed is answered, and told only what they are owed.
     *
     * <p>Refusing the whole call would leave them with a 403 and no way to learn that the
     * account is suspended, which is the one outcome that helps nobody. Reporting the plan
     * would be the platform acting on data it is not entitled to act on.
     */
    @Test
    void aSuspendedHolderLearnsTheyAreSuspendedAndNothingAboutTheirLearningData() {
        Account student = studentReadyToPlan(3);
        planFor(student);
        assertThat(states.of(student.id()).activePlanId()).isNotNull();

        consents.revoke(student.id(), ConsentPurpose.LEARNING_DATA_PROCESSING);

        StudentStateView state = states.of(student.id());
        assertThat(state.accountStatus()).isEqualTo(AccountStatus.SUSPENDED);
        assertThat(state.activePlanId()).isNull();
        assertThat(state.activeGenerationJobId()).isNull();
        assertThat(state.openSessionId()).isNull();
        assertThat(state.setupComplete()).isFalse();
    }
}
