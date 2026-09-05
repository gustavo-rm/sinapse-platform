package br.com.sinapse.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

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
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class PlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlatformApplication.class, args);
    }
}
