package br.com.sinapse.platform.identity.support;

import br.com.sinapse.platform.identity.internal.notification.AccountNotifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Beans every identity integration test needs.
 *
 * <p>Imported by one base class rather than by each test, so that the whole identity suite
 * shares a single application context instead of building one per configuration.
 */
@TestConfiguration
public class IdentityTestSupport {

    /**
     * Replaces the adapter that discards notifications with one that keeps them.
     *
     * @return the capturing notifier
     */
    @Bean
    @Primary
    public CapturingAccountNotifier capturingAccountNotifier() {
        return new CapturingAccountNotifier();
    }

    /**
     * Builds the states a test needs to start from.
     *
     * @return the fixtures
     */
    @Bean
    public IdentityFixtures identityFixtures() {
        return new IdentityFixtures();
    }
}
