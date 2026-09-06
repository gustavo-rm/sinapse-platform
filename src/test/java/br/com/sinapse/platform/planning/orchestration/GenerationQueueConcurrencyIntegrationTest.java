package br.com.sinapse.platform.planning.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.sinapse.platform.identity.internal.domain.Account;
import br.com.sinapse.platform.planning.internal.domain.PlanGenerationRequest;
import br.com.sinapse.platform.planning.support.PlanningIntegrationTest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Two workers, at the same time, on real connections.
 *
 * <p>This is what {@code for update skip locked} is for, and it is the one property of the
 * queue that cannot be checked by calling the claim twice in a row: sequential calls pass
 * whatever the locking does, because the first has already committed by the time the second
 * looks. Both transactions here are open, and each holds whatever it took, while the other one
 * selects.
 *
 * <p>What would happen without it: both workers read the same pending row, both mark it
 * running, and the student pays for two optimisation runs to get one plan — or worse, two plans
 * race to supersede each other.
 */
class GenerationQueueConcurrencyIntegrationTest extends PlanningIntegrationTest {

    /** Generous: a claim that is not blocked returns at once. */
    private static final int TIMEOUT_SECONDS = 30;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void twoWorkersNeverClaimTheSameJob() throws Exception {
        Account student = student();
        UUID job = insertPending(student, 0, null);

        List<List<UUID>> claimed = claimConcurrently();

        assertThat(claimed).hasSize(2);
        assertThat(claimed.stream().flatMap(List::stream).toList())
                .as("exactly one worker takes the job, and the other steps over it rather than "
                        + "waiting for it")
                .containsExactly(job);
        assertThat(claimed.stream().filter(List::isEmpty).count()).isEqualTo(1);
    }

    @Test
    void twoWorkersShareTheQueueRatherThanBlockingOnIt() throws Exception {
        UUID first = insertPending(student(), 0, null);
        UUID second = insertPending(student(), 0, null);

        List<List<UUID>> claimed = claimConcurrently();

        assertThat(claimed).allSatisfy(taken -> assertThat(taken).hasSize(1));
        assertThat(claimed.get(0)).doesNotContainAnyElementsOf(claimed.get(1));
        assertThat(claimed.stream().flatMap(List::stream).toList())
                .as("skip locked means the second worker takes the next row instead of queueing "
                        + "behind the first")
                .containsExactlyInAnyOrder(first, second);
    }

    /**
     * The backoff, checked against the statement that computes it.
     *
     * <p>Exercised directly rather than by waiting: the interval is per row and is derived from
     * that row's own attempt count, so what is worth asserting is the arithmetic, and a test
     * that slept would only be asserting that time passes.
     */
    @Test
    void aJustFailedJobIsNotClaimedUntilItsBackoffHasElapsed() {
        Account student = student();
        UUID job = insertPending(student, 1, clock.instant());

        assertThat(requests.claimPending(60, 10))
                .as("one attempt in, so it waits the base interval; it was attempted a moment ago")
                .isEmpty();

        assertThat(requests.claimPending(0, 10))
                .extracting(PlanGenerationRequest::id)
                .as("with no backoff configured it is eligible at once")
                .containsExactly(job);
    }

    @Test
    void theBackoffGrowsWithTheAttemptCount() {
        Account student = student();
        // Attempted three times, the last one ten minutes ago. With a base of five minutes the
        // third retry waits twenty, so ten minutes is not enough yet.
        insertPending(student, 3, clock.instant().minus(Duration.ofMinutes(10)));

        assertThat(requests.claimPending(300, 10)).isEmpty();
        assertThat(requests.claimPending(60, 10))
                .as("with a base of one minute the fourth attempt waits four, which elapsed "
                        + "long ago")
                .hasSize(1);
    }

    private List<List<UUID>> claimConcurrently() throws Exception {
        CyclicBarrier bothHolding = new CyclicBarrier(2);
        ExecutorService threads = Executors.newFixedThreadPool(2);
        try {
            Future<List<UUID>> one = threads.submit(claiming(bothHolding));
            Future<List<UUID>> other = threads.submit(claiming(bothHolding));
            return List.of(one.get(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    other.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        } finally {
            threads.shutdownNow();
        }
    }

    /**
     * One claim, on a connection and a transaction of its own.
     *
     * <p>The barrier is after the select and before the commit, so the second thread's select
     * runs while the first is still holding the rows it took. Without that, the two would be
     * sequential and the test would prove nothing.
     */
    private Callable<List<UUID>> claiming(CyclicBarrier bothHolding) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return () -> transaction.execute(status -> {
            // One at a time, which is the configured batch size: a worker that took the whole
            // queue would leave the other nothing and prove nothing about locking.
            List<UUID> claimed = requests.claimPending(0, 1).stream()
                    .map(PlanGenerationRequest::id)
                    .toList();
            try {
                bothHolding.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (Exception interrupted) {
                throw new IllegalStateException(interrupted);
            }
            return claimed;
        });
    }

    private UUID insertPending(Account student, int attemptCount, java.time.Instant startedAt) {
        UUID id = UUID.randomUUID();
        LocalDate start = LocalDate.now(clock);
        jdbc.update(INSERT_REQUEST, id, student.id(), "PENDING", start, start.plusWeeks(4));
        jdbc.update("update plan_generation_request set attempt_count = ?, started_at = ? "
                        + "where id = ?", attemptCount,
                startedAt == null ? null : Timestamp.from(startedAt), id);
        return id;
    }
}
