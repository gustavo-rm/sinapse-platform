package br.com.sinapse.platform.planning.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.sinapse.platform.IntegrationTest;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * The orphan bound follows the configuration it is derived from, through the real binding.
 *
 * <p>The three properties are set to values no default has, so that a bound that ignored any of
 * them — or a copy of the read timeout written down somewhere — would come out different.
 */
@TestPropertySource(properties = {
        "sinapse.core.connect-timeout=7s",
        "sinapse.core.read-timeout=25m",
        "sinapse.planning.generation.orphan-margin=90s"})
class OrphanBoundConfigurationIntegrationTest extends IntegrationTest {

    @Autowired
    private OrphanedJobRecovery recovery;

    @Test
    void theBoundIsComputedFromTheCoreTimeoutsAndTheConfiguredMargin() {
        assertThat(recovery.longestAttempt())
                .isEqualTo(Duration.ofSeconds(7).plus(Duration.ofMinutes(25))
                        .plus(Duration.ofSeconds(90)));
    }
}
