package br.com.sinapse.platform.identity.internal.web;

import br.com.sinapse.platform.identity.api.ConsentPurpose;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * An act of consent by a signed-in holder.
 *
 * @param purpose        purpose being consented to
 * @param termsVersionId identifier of the text the client displayed
 */
@Schema(description = "Consent to one purpose, against the wording that was displayed")
public record ConsentRequest(
        @NotNull ConsentPurpose purpose,
        @NotNull UUID termsVersionId) {
}
