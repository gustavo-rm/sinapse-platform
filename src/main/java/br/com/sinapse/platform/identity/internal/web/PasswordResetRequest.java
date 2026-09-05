package br.com.sinapse.platform.identity.internal.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * A request for a reset link.
 *
 * @param email address the link would be sent to
 */
@Schema(description = "Request of a password reset")
public record PasswordResetRequest(@NotBlank String email) {
}
