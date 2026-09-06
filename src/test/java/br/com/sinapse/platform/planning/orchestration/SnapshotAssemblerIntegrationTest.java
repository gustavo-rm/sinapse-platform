package br.com.sinapse.platform.planning.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.sinapse.platform.coreclient.contract.EdgeProvenance;
import br.com.sinapse.platform.coreclient.contract.EdgeStrength;
import br.com.sinapse.platform.coreclient.contract.PlanRequest;
import br.com.sinapse.platform.coreclient.contract.RecallRating;
import br.com.sinapse.platform.curriculum.api.CatalogCuration;
import br.com.sinapse.platform.curriculum.api.TopicView;
import br.com.sinapse.platform.identity.internal.domain.Account;
import br.com.sinapse.platform.identity.support.IdentityFixtures;
import br.com.sinapse.platform.learningrecord.api.SessionKind;
import br.com.sinapse.platform.learningrecord.api.StudySessionView;
import br.com.sinapse.platform.learningrecord.internal.service.StudySessionService;
import br.com.sinapse.platform.planning.internal.service.GenerationRequestService.ClaimedJob;
import br.com.sinapse.platform.planning.orchestration.support.OrchestrationIntegrationTest;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The one place four contexts meet, and what it produces.
 *
 * <p>Section 9.5 puts this composition here so that {@code planning} never reads
 * {@code learningrecord} itself. What the tests below check is that each of the four
 * contributions actually arrives in the document: availability resolved into real instants,
 * topics carrying an estimate adjusted for this student, the edges among them, and what the
 * student has already done.
 */
class SnapshotAssemblerIntegrationTest extends OrchestrationIntegrationTest {

    private static final long SEED = 99L;

    @Autowired
    private SnapshotAssembler assembler;

    @Autowired
    private StudySessionService sessions;

    @Autowired
    private CatalogCuration curationService;

    @Test
    void theWeeklyRoutineBecomesRealIntervalsInTheStudentsOwnZone() {
        Account student = student();
        LocalDate monday = nextMonday();
        availability.declare(student.id(), DayOfWeek.MONDAY, LocalTime.of(19, 0),
                LocalTime.of(21, 0), monday, null);
        goalWithTopics(student, 1);

        PlanRequest request = assembler.assemble(job(student, monday, monday.plusDays(13)), SEED);

        assertThat(request.availability())
                .as("two Mondays in a fortnight")
                .hasSize(2);
        assertThat(request.availability().getFirst().start())
                .as("nineteen hundred in São Paulo is twenty-two hundred UTC; the module that "
                        + "holds the account is the one that knows which")
                .isEqualTo(monday.atTime(19, 0).atZone(IdentityFixtures.DEFAULT_ZONE).toInstant());
        assertThat(request.availability().getFirst().end())
                .isEqualTo(monday.atTime(21, 0).atZone(IdentityFixtures.DEFAULT_ZONE).toInstant());
        assertThat(request.availability().get(1).start())
                .isEqualTo(monday.plusWeeks(1).atTime(19, 0)
                        .atZone(IdentityFixtures.DEFAULT_ZONE).toInstant());
    }

    @Test
    void aWindowThatDoesNotCoverTheHorizonContributesNothing() {
        Account student = student();
        LocalDate monday = nextMonday();
        availability.declare(student.id(), DayOfWeek.MONDAY, LocalTime.of(19, 0),
                LocalTime.of(21, 0), monday.minusWeeks(8), monday.minusWeeks(1));
        availability.declare(student.id(), DayOfWeek.TUESDAY, LocalTime.of(19, 0),
                LocalTime.of(21, 0), monday, null);
        goalWithTopics(student, 1);

        PlanRequest request = assembler.assemble(job(student, monday, monday.plusDays(6)), SEED);

        assertThat(request.availability())
                .as("a routine that ended last month is not availability for next month")
                .hasSize(1);
        assertThat(request.availability().getFirst().start())
                .isEqualTo(monday.plusDays(1).atTime(19, 0)
                        .atZone(IdentityFixtures.DEFAULT_ZONE).toInstant());
    }

    @Test
    void theTopicsCarryTheBandAndAnEstimateAdjustedForThisStudent() {
        Account student = student();
        declareWeekdayEvenings(student);
        List<TopicView> topics = goalWithTopics(student, 2);
        // Six sessions that each took sixty minutes where fifty were planned: this student
        // needs a fifth longer than the band says.
        for (int index = 0; index < 6; index++) {
            studied(student, topics.getFirst().id(), 50, 60);
        }

        PlanRequest request = assembler.assemble(job(student), SEED);

        assertThat(request.topics()).hasSize(2);
        assertThat(request.topics())
                .allSatisfy(topic -> {
                    assertThat(topic.effortTier())
                            .as("the band a curator judged travels alongside the minutes, so "
                                    + "the core can tell a calibrated estimate from a default")
                            .isEqualTo("STANDARD");
                    assertThat(topic.estimatedMinutes())
                            .as("fifty minutes of band, scaled by the ratio this student's own "
                                    + "sessions show (decision L1)")
                            .isEqualTo(60);
                });
    }

    @Test
    void aStudentWithNoHistoryGetsTheBandValueUnchanged() {
        Account student = studentReadyToPlan(2);

        PlanRequest request = assembler.assemble(job(student), SEED);

        assertThat(request.topics())
                .allSatisfy(topic -> assertThat(topic.estimatedMinutes()).isEqualTo(50));
        assertThat(request.history()).isEmpty();
    }

    @Test
    void whatTheStudentHasDoneTravelsPerTopicWithTheRatingsInOrder() {
        Account student = student();
        declareWeekdayEvenings(student);
        List<TopicView> topics = goalWithTopics(student, 2);
        UUID topicId = topics.getFirst().id();
        studied(student, topicId, 50, 40, br.com.sinapse.platform.learningrecord.api.RecallRating.AGAIN);
        studied(student, topicId, 50, 30, br.com.sinapse.platform.learningrecord.api.RecallRating.HARD);
        studied(student, topicId, 50, 20, br.com.sinapse.platform.learningrecord.api.RecallRating.GOOD);

        PlanRequest request = assembler.assemble(job(student), SEED);

        assertThat(request.history()).singleElement().satisfies(topic -> {
            assertThat(topic.topicId()).isEqualTo(topicId);
            assertThat(topic.sessionCount()).isEqualTo(3);
            assertThat(topic.totalMinutes()).isEqualTo(90);
            assertThat(topic.lastStudiedAt()).isNotNull();
            assertThat(topic.recallRatings())
                    .as("the sequence over time is the signal ADR 0008 accepted self-report "
                            + "for; oldest first, because a trajectory read backwards is a "
                            + "different trajectory")
                    .containsExactly(RecallRating.AGAIN, RecallRating.HARD, RecallRating.GOOD);
        });
    }

    @Test
    void thePrerequisiteEdgesTravelWithTheirStrengthAndProvenance() {
        Account student = student();
        declareWeekdayEvenings(student);
        List<TopicView> topics = goalWithTopics(student, 2);
        curationService.addEdge(new CatalogCuration.EdgeDefinition(topics.get(0).id(),
                topics.get(1).id(), br.com.sinapse.platform.curriculum.api.EdgeStrength.HARD,
                br.com.sinapse.platform.curriculum.api.EdgeProvenance.CURATED,
                "Livro-texto, capítulo 3", UUID.randomUUID()));

        PlanRequest request = assembler.assemble(job(student), SEED);

        assertThat(request.prerequisites()).singleElement().satisfies(edge -> {
            assertThat(edge.prerequisiteTopicId()).isEqualTo(topics.get(0).id());
            assertThat(edge.dependentTopicId()).isEqualTo(topics.get(1).id());
            assertThat(edge.strength()).isEqualTo(EdgeStrength.HARD);
            assertThat(edge.provenance())
                    .as("provenance is what makes the ablation experiment possible without "
                            + "adding instrumentation later")
                    .isEqualTo(EdgeProvenance.CURATED);
        });
    }

    @Test
    void theDocumentCarriesTheHorizonTheParametersAndTheSeed() {
        Account student = studentReadyToPlan(1);
        LocalDate from = LocalDate.now(clock);

        PlanRequest request = assembler.assemble(job(student, from, from.plusWeeks(4)), SEED);

        assertThat(request.contractVersion()).isEqualTo(PlanRequest.VERSION);
        assertThat(request.horizon().start()).isEqualTo(from);
        assertThat(request.horizon().end()).isEqualTo(from.plusWeeks(4));
        assertThat(request.randomSeed()).isEqualTo(SEED);
        assertThat(request.algorithmParams())
                .as("the parameters are stored beside the snapshot and the seed; without all "
                        + "four a plan generated today cannot be regenerated tomorrow")
                .containsKey("generations");
        assertThat(request.goals()).singleElement()
                .satisfies(goal -> assertThat(goal.priority()).isEqualTo(3));
    }

    @Test
    void aStudentWithNothingToPlanIsRefusedRatherThanSentEmptyHanded() {
        Account withoutGoals = student();
        declareWeekdayEvenings(withoutGoals);
        Account withoutAvailability = student();
        goalWithTopics(withoutAvailability, 1);

        assertThatThrownBy(() -> assembler.assemble(job(withoutGoals), SEED))
                .isInstanceOf(NothingToPlanException.class);
        assertThatThrownBy(() -> assembler.assemble(job(withoutAvailability), SEED))
                .as("sending an empty document and letting the core answer with an empty plan "
                        + "would turn a missing precondition into a plan of nothing")
                .isInstanceOf(NothingToPlanException.class);
    }

    private void studied(Account student, UUID topicId, int planned, int actual) {
        studied(student, topicId, planned, actual,
                br.com.sinapse.platform.learningrecord.api.RecallRating.GOOD);
    }

    private void studied(Account student, UUID topicId, int planned, int actual,
            br.com.sinapse.platform.learningrecord.api.RecallRating rating) {

        StudySessionView started = sessions.start(student.id(), topicId, UUID.randomUUID(),
                SessionKind.STUDY, planned);
        sessions.complete(student.id(), started.id(), rating, actual);
    }

    private ClaimedJob job(Account student) {
        LocalDate from = LocalDate.now(clock);
        return job(student, from, from.plusWeeks(4));
    }

    private ClaimedJob job(Account student, LocalDate from, LocalDate to) {
        return new ClaimedJob(UUID.randomUUID(), student.id(), from, to, 1);
    }

    private LocalDate nextMonday() {
        LocalDate today = LocalDate.now(clock);
        return today.with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.MONDAY));
    }
}
