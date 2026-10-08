package br.com.sinapse.platform.planning.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.sinapse.platform.coreclient.api.CoreUnavailableException;
import br.com.sinapse.platform.identity.internal.domain.Account;
import br.com.sinapse.platform.planning.api.GenerationRequestStatus;
import br.com.sinapse.platform.planning.api.GenerationRequestView;
import br.com.sinapse.platform.planning.orchestration.support.OrchestrationIntegrationTest;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * One seed per job, whichever attempt produces the plan.
 *
 * <p>The seed source is changed between attempts on purpose. If an attempt drew its own seed,
 * the second request would carry the new one and the record would either point at a seed that
 * produced nothing or have moved under the first attempt's snapshot; either way a plan could not
 * be reproduced from what its job says.
 */
class SeedPerJobIntegrationTest extends OrchestrationIntegrationTest {

    @Autowired
    private OrphanedJobRecovery recovery;

    @Test
    void aRetrySendsTheSeedTheFirstAttemptSentAndThatIsTheOneRecorded() {
        Account student = studentReadyToPlan(3);
        core.failNext(1, () -> new CoreUnavailableException("the core did not answer"));
        seeds.setSeed(111L);

        GenerationRequestView first = generate(student);
        assertThat(first.status()).isEqualTo(GenerationRequestStatus.PENDING);

        seeds.setSeed(222L);
        worker.runOnce();

        GenerationRequestView second = requestService.require(student.id(), first.id());
        assertThat(second.status()).isEqualTo(GenerationRequestStatus.READY);
        assertThat(second.attemptCount()).isEqualTo(2);
        assertThat(core.seedsReceived())
                .as("both attempts of the job carry the same seed")
                .containsExactly(111L, 111L);
        assertThat(requestColumn(first.id(), "random_seed", Long.class))
                .as("and the seed on the record is the one that produced the plan")
                .isEqualTo(111L);
    }

    @Test
    void theAttemptAfterADeadWorkerSendsTheSeedTheDeadWorkerRecorded() {
        Account student = studentReadyToPlan(3);
        UUID job = requestService.queue(student.id(), null).id();
        // What a worker that recorded its submission and then died leaves behind.
        jdbc.update("""
                update plan_generation_request
                   set status = 'RUNNING', attempt_count = 1, started_at = ?,
                       snapshot = cast('{}' as jsonb), random_seed = ?
                 where id = ?
                """, Timestamp.from(clock.instant().minus(recovery.longestAttempt())
                        .minus(Duration.ofMinutes(1))), 4242L, job);
        seeds.setSeed(1L);

        worker.runOnce();

        assertThat(requestService.require(student.id(), job).status())
                .isEqualTo(GenerationRequestStatus.READY);
        assertThat(core.seedsReceived()).containsExactly(4242L);
        assertThat(requestColumn(job, "random_seed", Long.class)).isEqualTo(4242L);
    }
}
