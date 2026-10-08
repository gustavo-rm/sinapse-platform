package br.com.sinapse.platform.planning.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.sinapse.platform.coreclient.api.CoreCallLimits;
import br.com.sinapse.platform.planning.api.PlanGenerationFailure;
import br.com.sinapse.platform.planning.internal.config.PlanningProperties;
import br.com.sinapse.platform.planning.internal.persistence.PlanGenerationRequestRepository;
import br.com.sinapse.platform.planning.internal.service.GenerationRequestService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The orphan bound and the cutoff it becomes, with the clock in the test's hands.
 *
 * <p>Which jobs the cutoff then selects is decided by the database, in one conditional
 * statement, and is tested against it in {@link OrphanedJobRecoveryIntegrationTest}: a unit test
 * of that decision would be a test of a mock. What is pure arithmetic is tested here.
 */
class OrphanedJobRecoveryTest {

    private static final Instant NOW = Instant.parse("2026-10-05T14:00:00Z");

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private final PlanGenerationRequestRepository repository =
            mock(PlanGenerationRequestRepository.class);

    @Test
    void theBoundIsTheLongestCallToTheCorePlusTheMargin() {
        OrphanedJobRecovery recovery = recovery(new CoreCallLimits(Duration.ofSeconds(5),
                Duration.ofMinutes(10)), Duration.ofMinutes(2), 3);

        assertThat(recovery.longestAttempt())
                .isEqualTo(Duration.ofMinutes(12).plusSeconds(5));
    }

    @Test
    void raisingATimeoutOrTheMarginRaisesTheBound() {
        assertThat(recovery(new CoreCallLimits(Duration.ofSeconds(5), Duration.ofMinutes(30)),
                Duration.ofMinutes(2), 3).longestAttempt())
                .as("a longer read timeout is a longer legitimate attempt")
                .isEqualTo(Duration.ofMinutes(32).plusSeconds(5));
        assertThat(recovery(new CoreCallLimits(Duration.ofSeconds(20), Duration.ofMinutes(10)),
                Duration.ofMinutes(2), 3).longestAttempt())
                .isEqualTo(Duration.ofMinutes(12).plusSeconds(20));
        assertThat(recovery(new CoreCallLimits(Duration.ofSeconds(5), Duration.ofMinutes(10)),
                Duration.ofMinutes(7), 3).longestAttempt())
                .isEqualTo(Duration.ofMinutes(17).plusSeconds(5));
    }

    @Test
    void theCutoffIsTheInjectedNowMinusTheBound() {
        OrphanedJobRecovery recovery = recovery(new CoreCallLimits(Duration.ofSeconds(5),
                Duration.ofMinutes(10)), Duration.ofMinutes(2), 3);
        Instant cutoff = NOW.minus(Duration.ofMinutes(12).plusSeconds(5));
        when(repository.recoverOrphans(cutoff, 3, NOW,
                PlanGenerationFailure.CORE_UNAVAILABLE.name())).thenReturn(1);

        assertThat(recovery.recover()).isEqualTo(1);

        verify(repository).recoverOrphans(cutoff, 3, NOW,
                PlanGenerationFailure.CORE_UNAVAILABLE.name());
    }

    private OrphanedJobRecovery recovery(CoreCallLimits limits, Duration margin, int maxAttempts) {
        PlanningProperties properties = new PlanningProperties(Duration.ofDays(90), 100,
                new PlanningProperties.Generation(Period.ofWeeks(4), maxAttempts,
                        Duration.ofSeconds(30), margin, 1, "-", Duration.ofDays(90), Map.of(), 5,
                        0.5, 2.0));
        GenerationRequestService requests = new GenerationRequestService(repository, null, null,
                null, properties, clock);
        return new OrphanedJobRecovery(requests, limits, properties);
    }
}
