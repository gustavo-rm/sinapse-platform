package br.com.sinapse.platform.identity.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.sinapse.platform.identity.internal.domain.AccountTokenPurpose;
import br.com.sinapse.platform.identity.internal.notification.AccountNotification;
import br.com.sinapse.platform.identity.internal.notification.DevOnlyLoggingAccountNotifier;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.env.MockEnvironment;

/** What the DEV ONLY notifier writes, and when it refuses to exist at all. */
class DevOnlyLoggingAccountNotifierTest {

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private final Logger logger = (Logger) LoggerFactory.getLogger(DevOnlyLoggingAccountNotifier.class);

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
    void underTheLocalProfileAloneItWritesTheTokenAndHowToRedeemItButNotTheAddress() {
        DevOnlyLoggingAccountNotifier notifier = new DevOnlyLoggingAccountNotifier(profiles("local"));
        UUID accountId = UUID.randomUUID();

        notifier.deliver(new AccountNotification(accountId, "aluno.sintetico@example.com",
                AccountTokenPurpose.EMAIL_VERIFICATION, "the-clear-token",
                Instant.parse("2026-10-06T12:00:00Z")));

        assertThat(appender.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.INFO);
            assertThat(event.getFormattedMessage())
                    .startsWith("DEV ONLY")
                    .contains(accountId.toString(), "the-clear-token",
                            "POST /api/v1/email-verifications")
                    .doesNotContain("aluno.sintetico@example.com");
        });
    }

    @Test
    void aPasswordResetNamesTheConfirmationRouteAndTheFieldItNeeds() {
        new DevOnlyLoggingAccountNotifier(profiles("local")).deliver(new AccountNotification(
                UUID.randomUUID(), "aluno.sintetico@example.com", AccountTokenPurpose.PASSWORD_RESET,
                "reset-token", Instant.parse("2026-10-06T12:00:00Z")));

        assertThat(appender.list).singleElement().satisfies(event -> assertThat(
                event.getFormattedMessage())
                .contains("POST /api/v1/password-resets/confirmation", "reset-token", "newPassword"));
    }

    @Test
    void anyOtherProfileAlongsideLocalRefusesToStart() {
        assertThatThrownBy(() -> new DevOnlyLoggingAccountNotifier(profiles("local", "prod")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DEV ONLY");
        assertThatThrownBy(() -> new DevOnlyLoggingAccountNotifier(profiles("prod", "local")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new DevOnlyLoggingAccountNotifier(profiles()))
                .as("constructed by hand with no profile at all, it still refuses")
                .isInstanceOf(IllegalStateException.class);
    }

    private static MockEnvironment profiles(String... active) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(active);
        return environment;
    }
}
