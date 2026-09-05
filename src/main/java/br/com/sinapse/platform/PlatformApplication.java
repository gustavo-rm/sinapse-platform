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
 * <p>{@code UserDetailsServiceAutoConfiguration} is excluded because, with no
 * authentication mechanism configured yet, it would invent an in-memory user and print its
 * generated password to the log. A credential must never appear in a log, and the real
 * mechanism — opaque server-side sessions, ADR 0010 — arrives with the identity module.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class PlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlatformApplication.class, args);
    }
}
