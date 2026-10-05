package br.com.sinapse.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Entry point of the Sinapse platform backend.
 *
 * <p>Declared {@code public} on purpose: a package-private main class cannot be
 * packaged into an executable jar by the Spring Boot Maven plugin.
 *
 * <p>{@code UserDetailsServiceAutoConfiguration} is excluded because it would invent an
 * in-memory user and print its generated password to the log, and a credential must never
 * appear in a log. The exclusion stays now that identity exists: authentication here is an
 * opaque server-side session (ADR 0010), resolved by a filter of the identity module, and
 * nothing in the platform loads a user by name and password through Spring Security.
 *
 * <p><strong>The catalogue command ends the process.</strong> Under the {@code catalog}
 * profile this same jar is a command line (ADR 0014). Its command runs during startup, but
 * the scheduler of the platform's periodic jobs keeps the JVM alive afterwards, so without an
 * explicit exit the command never returns and its exit code is never read.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class PlatformApplication {

    /** Profile under which the application is the catalogue command line. */
    private static final String CATALOG_PROFILE = "catalog";

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(PlatformApplication.class, args);
        if (context.getEnvironment().matchesProfiles(CATALOG_PROFILE)) {
            System.exit(SpringApplication.exit(context));
        }
    }
}
