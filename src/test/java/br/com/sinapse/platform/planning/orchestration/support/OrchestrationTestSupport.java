package br.com.sinapse.platform.planning.orchestration.support;

import br.com.sinapse.platform.planning.orchestration.SeedSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Replaces the two things a generation test cannot let the world decide.
 *
 * <p>The core, because it is a separate process that is not running during a build, and the
 * prompt for this work says to build against the contract and a stub rather than write a
 * fallback that generates plans. And the seed, because a random one makes the reproducibility
 * claim untestable.
 *
 * <p>Nothing else is replaced. The queue, the transactions, the triggers and the partial
 * indexes are the real ones.
 */
@TestConfiguration
public class OrchestrationTestSupport {

    /**
     * @return the deterministic stand-in for the optimiser
     */
    @Bean
    @Primary
    public StubSinapseCore stubSinapseCore() {
        return new StubSinapseCore();
    }

    /**
     * @return a seed the test chooses
     */
    @Bean
    @Primary
    public SeedSource fixedSeedSource() {
        return new FixedSeedSource();
    }
}
