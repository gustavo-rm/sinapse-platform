package br.com.sinapse.platform.identity.internal.config;

import br.com.sinapse.platform.identity.internal.service.TermsService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Beans the identity module contributes to the application.
 *
 * <p>Scheduling is enabled here rather than centrally because identity is the only module
 * with scheduled work so far. When a second one appears — the generation job of
 * {@code planning} will — this annotation moves to the shared configuration, and nothing
 * else changes.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class IdentityConfiguration {

    /**
     * Argon2id, as decided in section 5.8. BCrypt would be acceptable; Argon2id is better
     * and choosing it now costs nothing.
     *
     * <p>The parameters are Spring Security's own defaults for version 5.8 — 16 KiB of salt,
     * 32 bytes of output, one lane, 16 MiB of memory, two passes — rather than numbers
     * invented here. Raising the cost is a deployment decision that needs measurement on the
     * hardware it will run on, and inventing it in code is how a login route becomes a
     * denial-of-service surface.
     *
     * @return the encoder every password in the platform passes through
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    /**
     * Publishes the wordings supplied by the operator, once the context is up.
     *
     * <p>Idempotent by {@code (purpose, version)}: it only ever inserts what is missing, and
     * never rewrites a wording a consent record already points at.
     *
     * @param terms the terms service
     * @return the runner
     */
    @Bean
    public ApplicationRunner termsCatalogInitializer(TermsService terms) {
        return args -> terms.publishConfigured();
    }
}
