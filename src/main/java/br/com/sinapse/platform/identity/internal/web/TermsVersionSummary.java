package br.com.sinapse.platform.identity.internal.web;

import br.com.sinapse.platform.identity.api.ConsentPurpose;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * A published wording, with its full text.
 *
 * <p>The text is returned in full because the screen that collects an acceptance has to show
 * it. A summary, a link or a hash would each turn the acceptance into consent to something
 * the person did not read.
 *
 * @param id          identifier to submit when accepting
 * @param purpose     purpose the wording covers
 * @param version     label of the wording
 * @param body        full text
 * @param publishedAt when it came into force
 */
@Schema(description = "A published wording of the consent terms")
public record TermsVersionSummary(
        UUID id,
        ConsentPurpose purpose,
        String version,
        String body,
        Instant publishedAt) {
}
