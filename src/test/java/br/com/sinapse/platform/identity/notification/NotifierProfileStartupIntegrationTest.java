package br.com.sinapse.platform.identity.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.sinapse.platform.IntegrationTest;
import br.com.sinapse.platform.PlatformApplication;
import br.com.sinapse.platform.identity.internal.notification.AccountNotifier;
import br.com.sinapse.platform.identity.internal.notification.DevOnlyLoggingAccountNotifier;
import br.com.sinapse.platform.identity.internal.notification.LoggingAccountNotifier;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

/**
 * The application started for real, as it is deployed and as a developer runs it, to show that
 * what exists for development stays there.
 *
 * <p>Production runs on the base configuration with no profile active. That is the
 * configuration started here as "production": no test profile, no stubbed notifier, the same
 * jar contents an operator gets.
 */
@ExtendWith(OutputCaptureExtension.class)
class NotifierProfileStartupIntegrationTest extends IntegrationTest {

    @Autowired
    private Environment environment;

    @Test
    void inProductionTheDevOnlyNotifierDoesNotExistAndNothingAboutDeliveryChanges(
            CapturedOutput output) {
        try (ConfigurableApplicationContext context = start()) {
            assertThat(context.getBeansOfType(DevOnlyLoggingAccountNotifier.class)).isEmpty();
            assertThat(context.getBeansOfType(AccountNotifier.class).values())
                    .as("production keeps the one notifier it had, which drops the token")
                    .singleElement()
                    .isInstanceOf(LoggingAccountNotifier.class);
            assertThat(context.getBean(AccountNotifier.class))
                    .isInstanceOf(LoggingAccountNotifier.class);
        }
        assertThat(output.getAll())
                .as("the empty trusted-proxy list of the base configuration is warned about")
                .contains("trusted-proxies is empty");
    }

    @Test
    void underTheLocalProfileTheDevOnlyNotifierIsTheOneUsed(CapturedOutput output) {
        try (ConfigurableApplicationContext context = start("local")) {
            assertThat(context.getBean(AccountNotifier.class))
                    .isInstanceOf(DevOnlyLoggingAccountNotifier.class);
        }
        assertThat(output.getAll()).doesNotContain("trusted-proxies is empty");
    }

    @Test
    void localCombinedWithAnotherProfileFailsToStart() {
        assertThatThrownBy(() -> start("local", "prod").close())
                .as("fail closed: credentials in the log of anything but a lone local profile "
                        + "is a leak, so the startup is refused")
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DEV ONLY");
    }

    private ConfigurableApplicationContext start(String... profiles) {
        List<String> arguments = List.of(
                "--server.port=0",
                "--management.server.port=0",
                "--sinapse.planning.generation.poll-cron=-",
                "--spring.datasource.url=" + environment.getProperty("spring.datasource.url"),
                "--spring.datasource.username=" + environment.getProperty("spring.datasource.username"),
                "--spring.datasource.password=" + environment.getProperty("spring.datasource.password"));
        return new SpringApplicationBuilder(PlatformApplication.class)
                .profiles(profiles)
                .run(arguments.toArray(String[]::new));
    }
}
