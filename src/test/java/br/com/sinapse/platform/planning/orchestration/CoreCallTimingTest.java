package br.com.sinapse.platform.planning.orchestration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.sinapse.platform.coreclient.api.CoreProtocolException;
import br.com.sinapse.platform.coreclient.api.CoreUnavailableException;
import br.com.sinapse.platform.coreclient.api.SinapseCore;
import br.com.sinapse.platform.coreclient.contract.PlanRequest;
import br.com.sinapse.platform.coreclient.contract.PlanResponse;
import br.com.sinapse.platform.planning.api.PlanGenerationFailure;
import br.com.sinapse.platform.planning.internal.service.GenerationRequestService;
import br.com.sinapse.platform.planning.internal.service.GenerationRequestService.ClaimedJob;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The latency of the call to the core, with the clock in the test's hands.
 *
 * <p>The core reports no run time ({@code elapsedMillis} is a reserved 0, decision DT-2), so the
 * orchestrator times the call itself. What it records is the difference between two readings of
 * the injected clock taken around the call, nothing else: a fake clock that the fake core moves
 * forward is enough to pin that, and that a failed call records nothing.
 */
class CoreCallTimingTest {

    private static final Instant START = Instant.parse("2026-10-19T12:00:00Z");

    private final SteppingClock clock = new SteppingClock(START);

    private final SnapshotAssembler assembler = mock(SnapshotAssembler.class);

    private final GenerationRequestService requests = mock(GenerationRequestService.class);

    private final GeneratedPlanWriter writer = mock(GeneratedPlanWriter.class);

    private final ClaimedJob job = new ClaimedJob(UUID.randomUUID(), UUID.randomUUID(),
            LocalDate.of(2026, 10, 19), LocalDate.of(2026, 11, 16), 1);

    private final PlanRequest request = new PlanRequest(PlanRequest.VERSION,
            new PlanRequest.Horizon(job.horizonStart(), job.horizonEnd()), List.of(), List.of(),
            List.of(), List.of(), List.of(), Map.of(), 42L);

    private final PlanResponse response = new PlanResponse(PlanRequest.VERSION, List.of(),
            Map.of(), new PlanResponse.ExecutionMetadata("core-1", 42L, 0, 0));

    @BeforeEach
    void assembleAndRecord() {
        when(requests.recordedSeed(job.id())).thenReturn(OptionalLong.of(42L));
        when(assembler.assemble(job, 42L)).thenReturn(request);
    }

    @Test
    void theRecordedLatencyIsTheDifferenceTheClockMovedDuringTheCall() {
        orchestrator(sent -> {
            clock.advance(Duration.ofMillis(1_234));
            return response;
        }).run(job);

        verify(writer).store(job, response, 1_234L);
    }

    @Test
    void theClockIsReadAfterTheSnapshotIsRecordedSoOnlyTheCallIsTimed() {
        when(assembler.assemble(job, 42L)).thenAnswer(invocation -> {
            clock.advance(Duration.ofSeconds(30));
            return request;
        });

        orchestrator(sent -> {
            clock.advance(Duration.ofMillis(250));
            return response;
        }).run(job);

        verify(writer).store(job, response, 250L);
    }

    @Test
    void aWallClockThatStepsBackDuringTheCallIsRecordedAsZero() {
        orchestrator(sent -> {
            clock.advance(Duration.ofSeconds(-5));
            return response;
        }).run(job);

        verify(writer).store(job, response, 0L);
    }

    @Test
    void aRejectedCallRecordsNoSuccessAndNoLatency() {
        orchestrator(sent -> {
            clock.advance(Duration.ofMillis(900));
            throw new CoreProtocolException("the core rejected the request with status 422");
        }).run(job);

        verify(writer, never()).store(any(), any(), anyLong());
        verify(requests, never()).succeed(any(), any(), anyInt(), anyLong(), anyLong());
        verify(requests).failOrRetry(job.id(), PlanGenerationFailure.CORE_REJECTED);
    }

    @Test
    void eachAttemptTimesItsOwnCallAndOnlyTheOneThatProducedThePlanIsRecorded() {
        int[] attempt = {0};
        PlanGenerationOrchestrator orchestrator = orchestrator(sent -> {
            attempt[0]++;
            if (attempt[0] == 1) {
                clock.advance(Duration.ofSeconds(5));
                throw new CoreUnavailableException("the core did not answer");
            }
            clock.advance(Duration.ofMillis(700));
            return response;
        });

        orchestrator.run(job);
        verify(writer, never()).store(any(), any(), anyLong());

        orchestrator.run(job);
        verify(writer).store(job, response, 700L);
    }

    private PlanGenerationOrchestrator orchestrator(Function<PlanRequest, PlanResponse> answer) {
        SinapseCore core = answer::apply;
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        return new PlanGenerationOrchestrator(assembler, core, requests, writer, () -> 42L, mapper,
                clock);
    }

    /** A clock that stands still until it is told to move, in either direction. */
    static final class SteppingClock extends Clock {

        private Instant now;

        SteppingClock(Instant start) {
            this.now = start;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
