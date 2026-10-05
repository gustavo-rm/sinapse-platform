package br.com.sinapse.platform.shared.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.env.MockEnvironment;

/** When the startup warning about an empty trusted-proxy list is given, and when it is not. */
class TrustedProxiesStartupCheckTest {

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private final Logger logger = (Logger) LoggerFactory.getLogger(TrustedProxiesStartupCheck.class);

    @BeforeEach
    void capture() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void release() {
        logger.detachAppender(appender);
    }

    @Test
    void aDeployedInstanceWithNoTrustedProxyIsWarnedAbout() {
        new TrustedProxiesStartupCheck(properties(List.of()), profiles());

        assertThat(appender.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage())
                    .contains("trusted-proxies is empty", "share one rate-limit counter");
        });
    }

    @Test
    void aDeploymentProfileOfItsOwnIsStillADeployment() {
        new TrustedProxiesStartupCheck(properties(List.of()), profiles("prod"));

        assertThat(appender.list).singleElement()
                .satisfies(event -> assertThat(event.getLevel()).isEqualTo(Level.WARN));
    }

    @Test
    void aConfiguredProxyIsNotWarnedAbout() {
        new TrustedProxiesStartupCheck(properties(List.of("192.0.2.10/32")), profiles());

        assertThat(appender.list).isEmpty();
    }

    @Test
    void developmentProfilesAreNotWarnedAbout() {
        new TrustedProxiesStartupCheck(properties(List.of()), profiles("local"));
        new TrustedProxiesStartupCheck(properties(List.of()), profiles("test"));

        assertThat(appender.list).isEmpty();
    }

    private static RateLimitProperties properties(List<String> trustedProxies) {
        return new RateLimitProperties(trustedProxies, List.of());
    }

    private static MockEnvironment profiles(String... active) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(active);
        return environment;
    }
}
