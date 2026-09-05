package br.com.sinapse.platform.identity.internal.web;

import br.com.sinapse.platform.identity.api.ConsentGrantedBy;
import br.com.sinapse.platform.identity.api.ConsentPurpose;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * One entry of the caller's consent history.
 *
 * <p>Revoked entries are included, and that is the point. The holder is entitled to see what
 * they consented to and when they took it back; a history that showed only what is currently
 * in force would be a status display, not a record.
 *
 * <p>The evidence is not returned. It exists to discharge the controller's burden of proof
 * and contains an address the holder gains nothing from reading back.
 *
 * @param id             identifier of the record
 * @param purpose        purpose consented to
 * @param grantedBy      who granted it
 * @param termsVersionId wording accepted
 * @param termsVersion   label of that wording
 * @param grantedAt      when it was granted
 * @param revokedAt      when it was withdrawn, or {@code null} while in force
 */
@Schema(description = "One act of consent, granted or withdrawn")
public record ConsentSummary(
        UUID id,
        ConsentPurpose purpose,
        ConsentGrantedBy grantedBy,
        UUID termsVersionId,
        String termsVersion,
        Instant grantedAt,
        Instant revokedAt) {
}
