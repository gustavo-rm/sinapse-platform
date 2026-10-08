package br.com.sinapse.platform.planning.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.sinapse.platform.identity.internal.domain.Account;
import br.com.sinapse.platform.planning.api.GenerationRequestStatus;
import br.com.sinapse.platform.planning.api.GenerationRequestView;
import br.com.sinapse.platform.planning.api.PlanGenerationFailure;
import br.com.sinapse.platform.planning.internal.domain.PlanGenerationRequest;
import br.com.sinapse.platform.planning.orchestration.support.OrchestrationIntegrationTest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A worker that died mid-attempt, and the next pass of a worker that did not.
 *
 * <p>The crash is simulated the only way a test can: a job is put in the state a dead worker
 * leaves behind — {@code RUNNING}, one attempt spent, started long ago — and nothing is left to
 * finish it. Before the recovery existed that job stayed {@code RUNNING} forever and every new
 * request from the account answered {@code 409}.
 *
 * <p>The ages are placed one minute either side of the bound, which is read from the bean that
 * computes it. A test that hard-coded "an hour ago" would keep passing if the bound drifted to
 * fifty-nine minutes.
 */
class OrphanedJobRecoveryIntegrationTest extends OrchestrationIntegrationTest {

    private static final String GENERATION_REQUESTS = "/api/v1/study-plans/generation-requests";

    /** Generous: an update that is not blocked returns at once. */
    private static final int TIMEOUT_SECONDS = 30;

    @Autowired
    private OrphanedJobRecovery recovery;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Value("${sinapse.planning.generation.max-attempts}")
    private int maxAttempts;

    @Test
    void aJobLeftRunningByAWorkerThatDiedIsRecoveredAndTheAccountMayAskAgain() throws Exception {
        Account student = studentReadyToPlan(2);
        String token = tokenFor(student);
        UUID job = requestService.queue(student.id(), null).id();
        leftRunning(job, 1, pastTheBound());

        mockMvc.perform(post(GENERATION_REQUESTS)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                // The dead worker's job still counts as unfinished: this is the reported fault.
                .andExpect(status().isConflict());

        worker.runOnce();

        GenerationRequestView recovered = requestService.require(student.id(), job);
        assertThat(recovered.status())
                .as("given back to the queue and claimed again in the same pass")
                .isEqualTo(GenerationRequestStatus.READY);
        assertThat(recovered.attemptCount())
                .as("the dead worker's attempt was spent; this was the next one")
                .isEqualTo(2);
        assertThat(recovered.planId()).isNotNull();
        assertThat(core.callCount()).isEqualTo(1);
        mockMvc.perform(post(GENERATION_REQUESTS)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                // The account is no longer blocked.
                .andExpect(status().isCreated());
    }

    @Test
    void aJobStillWithinTheLongestAttemptIsLeftToItsWorker() throws Exception {
        Account student = studentReadyToPlan(2);
        UUID job = requestService.queue(student.id(), null).id();
        Instant started = withinTheBound();
        leftRunning(job, 1, started);

        worker.runOnce();

        PlanGenerationRequest untouched = requests.findById(job).orElseThrow();
        assertThat(untouched.status())
                .as("a ten-minute call to the core is legitimate; recovering it would run the "
                        + "same job twice")
                .isEqualTo(GenerationRequestStatus.RUNNING);
        assertThat(untouched.attemptCount()).isEqualTo(1);
        assertThat(untouched.startedAt()).isEqualTo(started);
        assertThat(core.callCount()).isZero();
        mockMvc.perform(post(GENERATION_REQUESTS)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(student)))
                .andExpect(status().isConflict());
    }

    /**
     * The three outcomes, against the statement that decides them.
     *
     * <p>Called without a worker pass, so that what is observed is the recovery and not a
     * subsequent claim.
     */
    @Test
    void onlyJobsPastTheBoundAreRecoveredAndTheAttemptBudgetDecidesWhereTheyGo() {
        UUID within = queuedFor(student());
        leftRunning(within, 1, withinTheBound());
        UUID pastWithAttemptsLeft = queuedFor(student());
        leftRunning(pastWithAttemptsLeft, maxAttempts - 1, pastTheBound());
        recordedSubmission(pastWithAttemptsLeft, 4242L);
        UUID pastWithoutAttempts = queuedFor(student());
        leftRunning(pastWithoutAttempts, maxAttempts, pastTheBound());

        assertThat(recovery.recover()).isEqualTo(2);

        assertThat(requests.findById(within).orElseThrow().status())
                .isEqualTo(GenerationRequestStatus.RUNNING);

        PlanGenerationRequest requeued = requests.findById(pastWithAttemptsLeft).orElseThrow();
        assertThat(requeued.status()).isEqualTo(GenerationRequestStatus.PENDING);
        assertThat(requeued.attemptCount()).isEqualTo(maxAttempts - 1);
        assertThat(requeued.finishedAt()).isNull();
        assertThat(requeued.failureReason()).isNull();
        assertThat(requeued.randomSeed())
                .as("the record of what was sent survives, and the next attempt reuses the seed")
                .isEqualTo(4242L);
        assertThat(requeued.snapshot()).containsKey("recorded");

        PlanGenerationRequest failed = requests.findById(pastWithoutAttempts).orElseThrow();
        assertThat(failed.status()).isEqualTo(GenerationRequestStatus.FAILED);
        assertThat(failed.failureReason())
                .as("the existing reason for a call to the core that produced no answer; no new "
                        + "value reaches the client")
                .isEqualTo(PlanGenerationFailure.CORE_UNAVAILABLE.name());
        assertThat(failed.finishedAt()).isNotNull();
        assertThat(failed.attemptCount()).isEqualTo(maxAttempts);

        assertThat(recovery.recover()).as("nothing left to recover").isZero();
    }

    /**
     * Two instances sweeping at once, on real connections.
     *
     * <p>The first holds its transaction open after its update; the second's update on the same
     * row has to wait for it and, once it commits, finds the row no longer {@code RUNNING}. A
     * read followed by a write would let both see it running and both act.
     */
    @Test
    void twoInstancesRecoveringAtOnceActOnAJobOnlyOnce() throws Exception {
        UUID job = queuedFor(student());
        leftRunning(job, 1, pastTheBound());
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        CountDownLatch firstUpdated = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = pool.submit(() -> transaction.execute(status -> {
                int recovered = requestService.recoverOrphans(recovery.longestAttempt());
                firstUpdated.countDown();
                await(releaseFirst);
                return recovered;
            }));
            assertThat(firstUpdated.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            Future<Integer> second = pool.submit(() -> transaction.execute(
                    status -> requestService.recoverOrphans(recovery.longestAttempt())));
            // Long enough for the second update to reach the row lock and wait on it.
            Thread.sleep(500);
            releaseFirst.countDown();

            assertThat(first.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    + second.get(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                    .as("the job was recovered once, by whichever instance got there first")
                    .isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(requests.findById(job).orElseThrow().status())
                .isEqualTo(GenerationRequestStatus.PENDING);
    }

    /** A minute older than the bound: no attempt can still be in progress. */
    private Instant pastTheBound() {
        return clock.instant().minus(recovery.longestAttempt()).minus(Duration.ofMinutes(1))
                .truncatedTo(ChronoUnit.MILLIS);
    }

    /** A minute younger than the bound: the call may still be under way. */
    private Instant withinTheBound() {
        return clock.instant().minus(recovery.longestAttempt()).plus(Duration.ofMinutes(1))
                .truncatedTo(ChronoUnit.MILLIS);
    }

    private UUID queuedFor(Account student) {
        declareWeekdayEvenings(student);
        goalWithTopics(student, 1);
        return requestService.queue(student.id(), null).id();
    }

    /** The state a worker leaves behind when it dies mid-attempt. */
    private void leftRunning(UUID job, int attemptCount, Instant startedAt) {
        jdbc.update("""
                update plan_generation_request
                   set status = 'RUNNING', attempt_count = ?, started_at = ?
                 where id = ?
                """, attemptCount, Timestamp.from(startedAt), job);
    }

    private void recordedSubmission(UUID job, long seed) {
        jdbc.update("""
                update plan_generation_request
                   set snapshot = cast('{"recorded": true}' as jsonb), random_seed = ?
                 where id = ?
                """, seed, job);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
