package br.com.sinapse.platform.identity.internal.web;

import br.com.sinapse.platform.identity.api.ConsentPurpose;
import br.com.sinapse.platform.identity.internal.domain.Account;
import br.com.sinapse.platform.identity.internal.service.EmailVerificationService;
import br.com.sinapse.platform.identity.internal.service.RegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Opening an account and proving control of its address.
 *
 * <p>Both routes are POST. The second one is reached from a link in a message, which would
 * naturally be a GET — but a verification changes state, and the CSRF decision of ADR 0009
 * holds only while no state-changing operation is reachable by GET. The link opens a page;
 * the page posts the token it was given.
 */
@RestController
@Tag(name = "Accounts", description = "Registration and verification of an account")
public class AccountController {

    private final RegistrationService registration;
    private final EmailVerificationService verification;
    private final RequestContext context;

    /**
     * @param registration registration use case
     * @param verification verification use case
     * @param context      resolution of the address and agent of the request
     */
    public AccountController(RegistrationService registration, EmailVerificationService verification,
            RequestContext context) {
        this.registration = registration;
        this.verification = verification;
        this.context = context;
    }

    /**
     * Registers an account.
     *
     * @param request submitted registration
     * @param http    current request, for the evidence of the consents given with it
     * @return the identifier and state of the new account
     */
    @PostMapping(value = IdentityRoutes.ACCOUNTS,
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Registers an account and records the consents given with it",
            description = "The account is created awaiting verification of its address. In this "
                    + "version an account holder below the configured consent age is refused: the "
                    + "guardian branch of the consent model exists in the schema but its flow is "
                    + "not implemented.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Account registered"),
            @ApiResponse(responseCode = "400", description = "Body rejected, with the offending fields",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Address already registered, or the "
                    + "accepted wording is no longer the one in force",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422", description = "Account holder below the consent age",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))})
    public ResponseEntity<RegistrationResponse> register(@Valid @RequestBody RegistrationRequest request,
            HttpServletRequest http) {

        Account account = registration.register(new RegistrationService.RegistrationCommand(
                request.email().trim(),
                request.password(),
                request.dateOfBirth(),
                ZoneId.of(request.timeZone()),
                acceptedTerms(request),
                context.evidenceOf(http)));

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new RegistrationResponse(account.id(), account.status()));
    }

    /**
     * Verifies an address.
     *
     * @param request the token presented
     * @return no content
     */
    @PostMapping(value = IdentityRoutes.EMAIL_VERIFICATIONS, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Consumes an e-mail verification token",
            description = "Single use. Verifying the address is what moves an account holder of age "
                    + "to ACTIVE, provided the essential consents are in force.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Address verified"),
            @ApiResponse(responseCode = "404", description = "Token unknown, expired or already used",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))})
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody EmailVerificationRequest request) {
        verification.verify(request.token());
        return ResponseEntity.noContent().build();
    }

    private static Map<ConsentPurpose, UUID> acceptedTerms(RegistrationRequest request) {
        Map<ConsentPurpose, UUID> accepted = new LinkedHashMap<>();
        request.acceptedTerms().forEach(entry -> accepted.put(entry.purpose(), entry.termsVersionId()));
        return accepted;
    }
}
