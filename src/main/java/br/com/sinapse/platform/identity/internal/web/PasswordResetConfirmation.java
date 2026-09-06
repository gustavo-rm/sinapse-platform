package br.com.sinapse.platform.identity.internal.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A new password, set against a reset token.
 *
 * @param token       value delivered to the registered address
 * @param newPassword password chosen
 */
@Schema(description = "Completion of a password reset")
public record PasswordResetConfirmation(
        @NotBlank String token,
        @NotBlank @Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_LENGTH) String newPassword) {
}
