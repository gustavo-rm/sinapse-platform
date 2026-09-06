package br.com.sinapse.platform.planning.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.sinapse.platform.coreclient.api.CoreProtocolException;
import br.com.sinapse.platform.coreclient.api.CoreUnavailableException;
import br.com.sinapse.platform.coreclient.contract.PlanRequest;
import br.com.sinapse.platform.coreclient.contract.PlanResponse;
import br.com.sinapse.platform.identity.internal.domain.Account;
import br.com.sinapse.platform.planning.api.GenerationRequestStatus;
import br.com.sinapse.platform.planning.api.GenerationRequestView;
import br.com.sinapse.platform.planning.api.PlanGenerationFailure;
import br.com.sinapse.platform.planning.api.PlanStatus;
import br.com.sinapse.platform.planning.api.PlannedSessionView;
import br.com.sinapse.platform.planning.api.StudyPlanView;
import br.com.sinapse.platform.planning.internal.domain.PlanGenerationRequest;
import br.com.sinapse.platform.planning.internal.error.GenerationAlreadyRunningException;
import br.com.sinapse.platform.planning.internal.error.SetupIncompleteException;
import br.com.sinapse.platform.planning.orchestration.support.OrchestrationIntegrationTest;
import br.com.sinapse.platform.planning.orchestration.support.StubSinapseCore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * A generation job from end to end, and what happens when the core does not cooperate.
 *
 * <p>The reproducibility check is the one this whole step exists for. ADR 0007 says four things
 * together make a plan repeatable — the snapshot, the core version, the parameters and the seed
 * — and a guarantee that has never been exercised is not a guarantee.
 */
class PlanGenerationIntegrationTest extends OrchestrationIntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(PlanGenerationIntegrationTest.class);

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void aQueuedJobBecomesAPlanAndRecordsWhatProducedIt() {
        Account student = studentReadyToPlan(3);

        GenerationRequestView job = generate(student);

        assertThat(job.status()).isEqualTo(GenerationRequestStatus.READY);
        assertThat(job.failureReason()).isNull();
        assertThat(job.planId()).isNotNull();
        assertThat(job.progress())
                .as("the core reports no progress and nothing here invents one (decision F4)")
                .isNull();

        StudyPlanView plan = directory.plan(job.planId()).orElseThrow();
        assertThat(plan.status()).isEqualTo(PlanStatus.ACTIVE);
        assertThat(plan.generationRequestId()).isEqualTo(job.id());
        assertThat(directory.sessionsOfPlan(plan.id())).hasSize(3);

        PlanGenerationRequest stored = requests.findById(job.id()).orElseThrow();
        assertThat(stored.snapshot())
                .as("the exact document that was sent, not a reassembly of the entities: "
                        + "availability and history move, and a plan explained by a document it "
                        + "was not generated from is explained by nothing")
                .isNotNull()
                .containsKey("topics");
        assertThat(stored.coreVersion()).isEqualTo(StubSinapseCore.VERSION);
        assertThat(stored.algorithmParams()).containsKey("generations");
        assertThat(stored.randomSeed()).isEqualTo(20260906L);
        assertThat(stored.startedAt()).isNotNull();
        assertThat(stored.finishedAt()).isNotNull();
        assertThat(stored.attemptCount()).isEqualTo(1);
    }

    /**
     * The verification ADR 0007 asks for: replay the stored run and get the same plan.
     *
     * <p>Nothing is reassembled. The snapshot is read back out of the job as JSON, turned into
     * the request it was, and put to the core again with the parameters and the seed that were
     * stored beside it. What comes back is compared session by session with what was written to
     * the database the first time.
     */
    @Test
    void aRunIsReproducibleFromWhatTheJobStored() {
        Account student = studentReadyToPlan(4);
        GenerationRequestView job = generate(student);
        List<PlannedSessionView> stored = directory.sessionsOfPlan(job.planId());

        PlanRequest replayed = storedRequestOf(job.id());
        PlanResponse replay = core.generate(replayed);

        assertThat(replay.sessions())
                .as("same snapshot, same parameters, same seed, same plan. Without the seed the "
                        + "algorithm is stochastic and this would fail")
                .hasSameSizeAs(stored);
        for (int index = 0; index < stored.size(); index++) {
            PlannedSessionView written = stored.get(index);
            PlanResponse.ScheduledSession replayedSession = replay.sessions().get(index);
            assertThat(replayedSession.topicId()).isEqualTo(written.topicId());
            assertThat(replayedSession.kind().name()).isEqualTo(written.kind().name());
            assertThat(replayedSession.scheduledStart()).isEqualTo(written.scheduledStart());
            assertThat(replayedSession.durationMinutes()).isEqualTo(written.durationMinutes());
            assertThat(replayedSession.sequenceIndex()).isEqualTo(written.sequenceIndex());
        }
        assertThat(replay.metadata().randomSeed()).isEqualTo(replayed.randomSeed());

        LOG.info("Reproducibility check: {} stored sessions, {} replayed, seed {}, core {} — "
                        + "topic, kind, start, duration and sequence identical on every one",
                stored.size(), replay.sessions().size(), replayed.randomSeed(),
                replay.metadata().coreVersion());
    }

    /**
     * The other half of the check above: without it, "identical" would be satisfied by a core
     * that ignores its input.
     */
    @Test
    void anotherSeedOverTheSameSnapshotProducesAnotherPlan() {
        Account student = studentReadyToPlan(4);
        GenerationRequestView job = generate(student);
        List<PlannedSessionView> stored = directory.sessionsOfPlan(job.planId());
        PlanRequest snapshot = storedRequestOf(job.id());

        PlanRequest withAnotherSeed = new PlanRequest(snapshot.contractVersion(),
                snapshot.horizon(), snapshot.availability(), snapshot.goals(), snapshot.topics(),
                snapshot.prerequisites(), snapshot.history(), snapshot.algorithmParams(),
                snapshot.randomSeed() + 1);

        List<UUID> replayedTopics = core.generate(withAnotherSeed).sessions().stream()
                .map(PlanResponse.ScheduledSession::topicId)
                .toList();

        assertThat(replayedTopics)
                .as("a genetic algorithm is stochastic; if the seed did not decide the outcome "
                        + "there would be nothing for the record to pin down")
                .isNotEqualTo(stored.stream().map(PlannedSessionView::topicId).toList());
    }

    @Test
    void anUnreachableCoreIsAttemptedAgainAndThenGivenUpOn() {
        Account student = studentReadyToPlan(2);
        core.alwaysFail(() -> new CoreUnavailableException("the core did not answer"));

        GenerationRequestView first = generate(student);

        assertThat(first.status())
                .as("the first failure puts the job back in the queue: nothing about the "
                        + "request was wrong, so the same request may work later")
                .isEqualTo(GenerationRequestStatus.PENDING);
        assertThat(first.attemptCount()).isEqualTo(1);

        worker.runOnce();
        GenerationRequestView second = requestService.require(student.id(), first.id());

        assertThat(second.status()).isEqualTo(GenerationRequestStatus.FAILED);
        assertThat(second.failureReason()).isEqualTo(PlanGenerationFailure.CORE_UNAVAILABLE);
        assertThat(second.attemptCount())
                .as("the test profile allows two attempts, and the second one spends the budget")
                .isEqualTo(2);
        assertThat(core.callCount()).isEqualTo(2);
        assertThat(planCount(student.id())).isZero();
    }

    @Test
    void aCoreThatAnswersWronglyIsNotAttemptedAgain() {
        Account student = studentReadyToPlan(2);
        core.alwaysFail(() -> new CoreProtocolException("the core produced no sessions"));

        GenerationRequestView job = generate(student);

        assertThat(job.status()).isEqualTo(GenerationRequestStatus.FAILED);
        assertThat(job.failureReason()).isEqualTo(PlanGenerationFailure.CORE_REJECTED);
        assertThat(core.callCount())
                .as("the payload would be identical on the next attempt and so would the "
                        + "answer; retrying would spend the budget establishing that")
                .isEqualTo(1);
        assertThat(planCount(student.id())).isZero();
    }

    /**
     * A core failure must never disturb what the student already has.
     *
     * <p>The plan write and the job's completion are one transaction, so there is no state in
     * which a plan was superseded by a plan that was never written.
     */
    @Test
    void aFailedRunLeavesThePreviousPlanInForce() {
        Account student = studentReadyToPlan(3);
        GenerationRequestView succeeded = generate(student);
        UUID originalPlan = succeeded.planId();
        List<UUID> originalSessions = directory.sessionsOfPlan(originalPlan).stream()
                .map(PlannedSessionView::id)
                .toList();

        core.alwaysFail(() -> new CoreProtocolException("the core produced no sessions"));
        GenerationRequestView failed = generate(student);

        assertThat(failed.status()).isEqualTo(GenerationRequestStatus.FAILED);
        assertThat(directory.activePlanOf(student.id()))
                .map(StudyPlanView::id)
                .as("the student keeps the plan they had; a failure is not a reason to take it "
                        + "away")
                .contains(originalPlan);
        assertThat(directory.sessionsOfPlan(originalPlan))
                .extracting(PlannedSessionView::id)
                .containsExactlyElementsOf(originalSessions);
        assertThat(planCount(student.id())).isEqualTo(1);
    }

    /**
     * The failure nobody anticipated is still a failure the job has to record.
     *
     * <p>A run that threw its way out would leave the job {@code RUNNING} forever, and the
     * partial index would then stop the student from ever asking for a plan again.
     */
    @Test
    void anUnanticipatedFailureStillEndsTheJob() {
        Account student = studentReadyToPlan(2);
        core.alwaysFail(() -> new IllegalStateException("something nobody thought about"));

        GenerationRequestView job = generate(student);

        assertThat(job.status()).isEqualTo(GenerationRequestStatus.FAILED);
        assertThat(job.failureReason()).isEqualTo(PlanGenerationFailure.INTERNAL);
        assertThat(job.finishedAt()).isNotNull();
        assertThat(planCount(student.id())).isZero();
        assertThat(requestService.unfinishedJobOf(student.id()))
                .as("the account is free to ask again, which it would not be if the job were "
                        + "left running")
                .isEmpty();
    }

    @Test
    void aJobWithNothingLeftToPlanEndsWithoutAPlan() {
        Account student = studentReadyToPlan(2);
        GenerationRequestView queued = requestService.queue(student.id(), null);
        // Between queuing and running, the student abandons what they were pursuing.
        directory.activeGoalsOf(student.id())
                .forEach(goal -> goals.abandon(student.id(), goal.id()));

        worker.runOnce();

        GenerationRequestView job = requestService.require(student.id(), queued.id());
        assertThat(job.status()).isEqualTo(GenerationRequestStatus.FAILED);
        assertThat(job.failureReason()).isEqualTo(PlanGenerationFailure.NOTHING_TO_PLAN);
        assertThat(core.callCount())
                .as("there is nothing to ask the optimiser for")
                .isZero();
    }

    @Test
    void aPlanIsRefusedBeforeThereIsAnythingToPlan() {
        Account withNothing = student();
        Account withOnlyAvailability = student();
        declareWeekdayEvenings(withOnlyAvailability);
        Account withOnlyAGoal = student();
        goalWithTopics(withOnlyAGoal, 2);

        assertThatThrownBy(() -> requestService.queue(withNothing.id(), null))
                .as("decision F2: without availability and a goal the optimiser has nothing to "
                        + "optimise, and a plan built from defaults would be fiction presented "
                        + "as a recommendation")
                .isInstanceOf(SetupIncompleteException.class);
        assertThatThrownBy(() -> requestService.queue(withOnlyAvailability.id(), null))
                .isInstanceOf(SetupIncompleteException.class);
        assertThatThrownBy(() -> requestService.queue(withOnlyAGoal.id(), null))
                .isInstanceOf(SetupIncompleteException.class);
    }

    @Test
    void aSecondJobIsRefusedWhileOneHasNotFinished() {
        Account student = studentReadyToPlan(2);
        requestService.queue(student.id(), null);

        assertThatThrownBy(() -> requestService.queue(student.id(), null))
                .as("each run costs minutes of CPU; without the limit an impatient student "
                        + "queues dozens")
                .isInstanceOf(GenerationAlreadyRunningException.class);
    }

    @Test
    void replanningSupersedesThePlanItReplaces() {
        Account student = studentReadyToPlan(3);
        GenerationRequestView first = generate(student);
        seeds.setSeed(777L);

        GenerationRequestView second = generate(student);

        assertThat(second.status()).isEqualTo(GenerationRequestStatus.READY);
        assertThat(directory.plan(first.planId()).orElseThrow())
                .satisfies(superseded -> {
                    assertThat(superseded.status()).isEqualTo(PlanStatus.SUPERSEDED);
                    assertThat(superseded.supersededByPlanId()).isEqualTo(second.planId());
                });
        assertThat(directory.sessionsOfPlan(first.planId()))
                .as("executed sessions reference these; re-planning preserves them")
                .hasSize(3);
    }

    @Test
    void theCatalogueRevisionInEffectIsRecordedOnTheJob() {
        Account student = studentReadyToPlan(2);
        UUID revision = UUID.randomUUID();
        jdbc.update("insert into catalog_import (id, source_revision) values (?, ?)", revision,
                "0f1e2d3c");

        GenerationRequestView job = requestService.queue(student.id(), revision);

        assertThat(requestColumn(job.id(), "catalog_import_id", UUID.class))
                .as("the snapshot carries the edges but not which curation state produced them, "
                        + "and the ablation experiment has to attribute a result to a revision")
                .isEqualTo(revision);
    }

    /** The snapshot, read back out of the job exactly as it was stored. */
    private PlanRequest storedRequestOf(UUID requestId) {
        Map<String, Object> snapshot = requests.findById(requestId).orElseThrow().snapshot();
        return objectMapper.convertValue(snapshot, PlanRequest.class);
    }
}
