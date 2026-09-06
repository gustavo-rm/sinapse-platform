package br.com.sinapse.platform.identity.internal.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A credential.
 *
 * <p>The address is not annotated {@code @Email}. A malformed address cannot authenticate
 * anything, and reporting it as a validation error would answer differently for a value that
 * could be a registered address and one that could not.
 *
 * @param email    address of the account holder
 * @param password password
 */
@Schema(description = "Credential presented to open a session")
public record LoginRequest(
        @NotBlank String email,
        @NotBlank @Size(max = PasswordPolicy.MAX_LENGTH) String password) {
}
