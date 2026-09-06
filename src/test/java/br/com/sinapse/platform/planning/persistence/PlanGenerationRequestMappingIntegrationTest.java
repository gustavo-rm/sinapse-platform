package br.com.sinapse.platform.planning.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.sinapse.platform.identity.internal.domain.Account;
import br.com.sinapse.platform.planning.api.GenerationRequestStatus;
import br.com.sinapse.platform.planning.internal.domain.PlanGenerationRequest;
import br.com.sinapse.platform.planning.support.PlanningIntegrationTest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The generation job as a persisted structure.
 *
 * <p>Nothing in this version claims a job, calls the core or assembles a snapshot. What is
 * asserted here is only that the row this module will queue work through is mapped correctly
 * and comes back as it went in — the consumer is the next step of the build, and a mapping
 * that turns out to be wrong then is a mapping that was never exercised now.
 */
class PlanGenerationRequestMappingIntegrationTest extends PlanningIntegrationTest {

    @Test
    void aQueuedJobRoundTripsThroughItsMapping() {
        Account student = student();
        LocalDate start = LocalDate.now(clock);
        UUID id = UUID.randomUUID();
        Instant requestedAt = clock.instant();

        requests.saveAndFlush(new PlanGenerationRequest(id, student.id(), start,
                start.plusWeeks(4), requestedAt));

        assertThat(requests.findById(id)).hasValueSatisfying(request -> {
            assertThat(request.status()).isEqualTo(GenerationRequestStatus.PENDING);
            assertThat(request.accountId()).isEqualTo(student.id());
            assertThat(request.horizonStart()).isEqualTo(start);
            assertThat(request.horizonEnd()).isEqualTo(start.plusWeeks(4));
            assertThat(request.attemptCount()).isZero();
            assertThat(request.requestedAt())
                    .as("the clock ticks in microseconds, which is the resolution timestamptz "
                            + "keeps, so what went in is what comes back")
                    .isEqualTo(requestedAt);
            assertThat(request.snapshot())
                    .as("the four fields that make a plan reproducible are filled by the "
                            + "consumer, which does not exist yet")
                    .isNull();
            assertThat(request.coreVersion()).isNull();
            assertThat(request.algorithmParams()).isNull();
            assertThat(request.randomSeed()).isNull();
        });
    }

    @Test
    void theUnfinishedJobOfAnAccountIsFoundByItsState() {
        Account student = student();
        LocalDate start = LocalDate.now(clock);
        requests.saveAndFlush(new PlanGenerationRequest(UUID.randomUUID(), student.id(), start,
                start.plusWeeks(4), clock.instant()));

        assertThat(requests.findByAccountIdAndStatusIn(student.id(),
                List.of(GenerationRequestStatus.PENDING, GenerationRequestStatus.RUNNING)))
                .as("at most one, by the partial index: each run costs minutes of CPU and "
                        + "without it an impatient student queues dozens")
                .isPresent();
        assertThat(requests.findByAccountIdAndStatusIn(student().id(),
                List.of(GenerationRequestStatus.PENDING, GenerationRequestStatus.RUNNING)))
                .isEmpty();
    }

    /**
     * The four fields that make a plan reproducible, read back through the mapping.
     *
     * <p>Written through SQL because the consumer that fills them does not exist yet, and read
     * back through the entity because that is the half being tested. Two of them are JSONB and
     * the mapping of those is the part most likely to be wrong: finding that out when the
     * consumer is written would mean finding it out with a snapshot in hand.
     */
    @Test
    void theFieldsThatMakeAPlanReproducibleSurviveTheMapping() {
        Account student = student();
        LocalDate start = LocalDate.now(clock);
        UUID id = UUID.randomUUID();
        jdbc.update(INSERT_REQUEST, id, student.id(), "PENDING", start, start.plusWeeks(4));
        jdbc.update("""
                update plan_generation_request
                   set status = 'READY',
                       snapshot = cast(? as jsonb),
                       core_version = ?,
                       algorithm_params = cast(? as jsonb),
                       random_seed = ?,
                       started_at = now(),
                       finished_at = now(),
                       attempt_count = 2
                 where id = ?
                """, "{\"topics\": 12}", "core-1.4.0", "{\"generations\": 400}", 42L, id);

        assertThat(requests.findById(id)).hasValueSatisfying(request -> {
            assertThat(request.status()).isEqualTo(GenerationRequestStatus.READY);
            assertThat(request.snapshot())
                    .as("the payload sent to the core, kept exactly as it was sent and "
                            + "deliberately not normalised")
                    .containsEntry("topics", 12);
            assertThat(request.coreVersion()).isEqualTo("core-1.4.0");
            assertThat(request.algorithmParams()).containsEntry("generations", 400);
            assertThat(request.randomSeed())
                    .as("a genetic algorithm is stochastic: without the seed the same snapshot "
                            + "and the same parameters produce a different plan")
                    .isEqualTo(42L);
            assertThat(request.startedAt()).isNotNull();
            assertThat(request.finishedAt()).isNotNull();
            assertThat(request.attemptCount()).isEqualTo(2);
            assertThat(request.failureReason()).isNull();
            assertThat(request.catalogImportId()).isNull();
        });
    }

    @Test
    void everyStateSaysWhetherItHasFinished() {
        assertThat(GenerationRequestStatus.PENDING.isTerminal()).isFalse();
        assertThat(GenerationRequestStatus.RUNNING.isTerminal()).isFalse();
        assertThat(GenerationRequestStatus.READY.isTerminal()).isTrue();
        assertThat(GenerationRequestStatus.FAILED.isTerminal()).isTrue();
        assertThat(GenerationRequestStatus.CANCELLED.isTerminal()).isTrue();
    }
}
