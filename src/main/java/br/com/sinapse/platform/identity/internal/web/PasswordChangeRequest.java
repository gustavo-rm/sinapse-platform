package br.com.sinapse.platform.identity.internal.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A new password, set by a holder who knows the current one.
 *
 * @param currentPassword password in force
 * @param newPassword     password chosen
 */
@Schema(description = "Change of password by the signed-in holder")
public record PasswordChangeRequest(
        @NotBlank @Size(max = PasswordPolicy.MAX_LENGTH) String currentPassword,
        @NotBlank @Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_LENGTH) String newPassword) {
}
