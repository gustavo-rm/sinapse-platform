package br.com.sinapse.platform.identity.internal.web;

import br.com.sinapse.platform.identity.api.ConsentPurpose;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * What a registration submits.
 *
 * <p>The accepted wordings are submitted as identifiers of the texts the client displayed,
 * not as a boolean. A boolean records that a box was ticked; an identifier records what the
 * person was looking at when they ticked it, and only the second is worth anything as proof.
 *
 * @param email         address of the account holder
 * @param password      chosen password
 * @param dateOfBirth   self-declared date of birth
 * @param timeZone      IANA zone the holder's local times are interpreted in
 * @param acceptedTerms the wordings accepted, one entry per purpose
 */
@Schema(description = "Registration of a new account, with the consents given at the time")
public record RegistrationRequest(

        @NotBlank @Email
        @Schema(example = "aluno@example.com")
        String email,

        @NotBlank @Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_LENGTH)
        @Schema(minLength = PasswordPolicy.MIN_LENGTH, maxLength = PasswordPolicy.MAX_LENGTH)
        String password,

        @NotNull @Past
        @Schema(example = "2001-04-17", description = "Self-declared. Registration below the "
                + "configured consent age is refused in this version")
        LocalDate dateOfBirth,

        @NotBlank @IanaZoneId
        @Schema(example = "America/Sao_Paulo")
        String timeZone,

        @NotEmpty @Valid
        List<TermsAcceptance> acceptedTerms) {

    /**
     * Every essential purpose has to be accepted, and none twice.
     *
     * <p>Expressed as a constraint rather than as a check inside the service so that the
     * answer is the shared validation body, naming the offending field, instead of a
     * conflict the client has to guess the meaning of.
     *
     * @return whether the acceptance list covers exactly what it must
     */
    @AssertTrue(message = "must accept each essential purpose exactly once")
    @Schema(hidden = true)
    public boolean isEssentialPurposesAccepted() {
        if (acceptedTerms == null) {
            return true;
        }
        Set<ConsentPurpose> purposes = purposes();
        return purposes.size() == acceptedTerms.size()
                && purposes.containsAll(ConsentPurpose.essentialPurposes());
    }

    /**
     * Sharing with an institution is not consented to here.
     *
     * <p>Decision F1 of the flows document: it is consented to when an invite is redeemed,
     * where the holder can be shown the classroom, the teacher and exactly what the teacher
     * will see. Consenting up front to something the holder may never do is not informed
     * consent, and this is the constraint that keeps it from happening by accident.
     *
     * @return whether the acceptance list stays out of that purpose
     */
    @AssertTrue(message = "sharing with an institution is consented to when an invite is redeemed")
    @Schema(hidden = true)
    public boolean isInstitutionSharingAbsent() {
        return acceptedTerms == null || !purposes().contains(ConsentPurpose.INSTITUTION_SHARING);
    }

    private Set<ConsentPurpose> purposes() {
        return acceptedTerms.stream()
                .map(TermsAcceptance::purpose)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /**
     * One accepted wording.
     *
     * @param purpose        purpose being consented to
     * @param termsVersionId identifier of the text the client displayed
     */
    public record TermsAcceptance(
            @NotNull ConsentPurpose purpose,
            @NotNull UUID termsVersionId) {
    }
}
