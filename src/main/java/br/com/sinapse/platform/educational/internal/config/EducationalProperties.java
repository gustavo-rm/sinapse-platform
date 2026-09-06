package br.com.sinapse.platform.educational.internal.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration of the educational module, bound from {@code sinapse.educational}.
 *
 * @param defaultInviteLifetime how long an invite lasts when the teacher does not say. Expiry
 *                              is mandatory on every invite — a code that never expires is a
 *                              credential in clear text that nobody remembers exists — so this
 *                              is a default, never an "off"
 * @param maxInviteLifetime     the longest an invite may be asked to last. Without a ceiling
 *                              the mandatory expiry is a formality that a client can set to a
 *                              century
 */
@Validated
@ConfigurationProperties("sinapse.educational")
public record EducationalProperties(

        @NotNull @DefaultValue("14d") Duration defaultInviteLifetime,

        @NotNull @DefaultValue("90d") Duration maxInviteLifetime) {
}
