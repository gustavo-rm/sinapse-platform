package br.com.sinapse.platform.identity.internal.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * The verification token, presented by the page the link opened.
 *
 * @param token value delivered to the registered address
 */
@Schema(description = "Consumption of an e-mail verification token")
public record EmailVerificationRequest(@NotBlank String token) {
}
