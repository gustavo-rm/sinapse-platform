package br.com.sinapse.platform.identity.internal.web;

import br.com.sinapse.platform.identity.api.ConsentPurpose;
import br.com.sinapse.platform.identity.internal.domain.TermsVersion;
import br.com.sinapse.platform.identity.internal.persistence.ConsentRecordRepository;
import br.com.sinapse.platform.identity.internal.persistence.TermsVersionRepository;
import br.com.sinapse.platform.identity.internal.security.AuthenticatedAccount;
import br.com.sinapse.platform.identity.internal.security.CurrentAccount;
import br.com.sinapse.platform.identity.internal.service.ConsentService;
import br.com.sinapse.platform.identity.internal.service.MajorityReaffirmationService;
import br.com.sinapse.platform.identity.internal.service.TermsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reading the terms, giving consent, withdrawing it, and reaffirming it after reaching the
 * age threshold.
 *
 * <p>Withdrawing an essential purpose suspends the account, and this route does not warn
 * about it or ask for confirmation. That belongs to the client, which can show the
 * consequence in the holder's own language; an API that refused to obey until asked twice
 * would be an API that makes withdrawal harder than granting, which is precisely what a
 * freely given consent may not be.
 */
@RestController
@Tag(name = "Consent", description = "Terms, consent and its withdrawal")
public class ConsentController {

    private final ConsentService consents;
    private final MajorityReaffirmationService reaffirmation;
    private final TermsService terms;
    private final ConsentRecordRepository records;
    private final TermsVersionRepository wordings;
    private final RequestContext context;

    /**
     * @param consents      the single writer of consent records
     * @param reaffirmation reaffirmation use case
     * @param terms         published wordings
     * @param records       consent records, read for the history
     * @param wordings      wordings, read to label the history
     * @param context       resolution of the address and agent of the request
     */
    public ConsentController(ConsentService consents, MajorityReaffirmationService reaffirmation,
            TermsService terms, ConsentRecordRepository records, TermsVersionRepository wordings,
            RequestContext context) {
        this.consents = consents;
        this.reaffirmation = reaffirmation;
        this.terms = terms;
        this.records = records;
        this.wordings = wordings;
        this.context = context;
    }

    /**
     * The published wordings.
     *
     * @return every wording, newest first
     */
    @GetMapping(value = IdentityRoutes.TERMS, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Lists the published wordings of the consent terms",
            description = "Open, because the screen that collects a registration has to show the "
                    + "full text before there is an account to attribute the reading to.")
    @ApiResponse(responseCode = "200", description = "The wordings, with their full text")
    public List<TermsVersionSummary> terms() {
        return terms.published().stream().map(ConsentController::summaryOf).toList();
    }

    /**
     * The caller's consent history.
     *
     * @return every act of consent, granted or withdrawn, most recent first
     */
    @GetMapping(value = IdentityRoutes.CONSENTS, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Lists the caller's own consent history",
            description = "Includes withdrawn records: the holder is entitled to see what they "
                    + "consented to and when they took it back.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The history"),
            @ApiResponse(responseCode = "401", description = "No usable session on the request",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))})
    @Transactional(readOnly = true)
    public List<ConsentSummary> history() {
        AuthenticatedAccount caller = CurrentAccount.require();
        Map<UUID, TermsVersion> byId = wordings.findAll().stream()
                .collect(Collectors.toMap(TermsVersion::id, Function.identity()));

        return records.findByAccountIdOrderByGrantedAtDesc(caller.accountId()).stream()
                .map(record -> new ConsentSummary(
                        record.id(),
                        record.purpose(),
                        record.grantedBy(),
                        record.termsVersionId(),
                        Optional.ofNullable(byId.get(record.termsVersionId()))
                                .map(TermsVersion::version)
                                .orElse(null),
                        record.grantedAt(),
                        record.revokedAt()))
                .toList();
    }

    /**
     * Consents to a purpose.
     *
     * @param request purpose and the wording that was displayed
     * @param http    current request, for the evidence
     * @return no content
     */
    @PostMapping(value = IdentityRoutes.CONSENTS, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Consents to one purpose",
            description = "The grantor is not submitted: it is derived from the holder's age at "
                    + "this moment, read in the holder's own time zone.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Consent recorded"),
            @ApiResponse(responseCode = "401", description = "No usable session on the request",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "The purpose already has a consent in "
                    + "force, or the accepted wording is no longer the one published",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))})
    public ResponseEntity<Void> grant(@Valid @RequestBody ConsentRequest request, HttpServletRequest http) {
        AuthenticatedAccount caller = CurrentAccount.require();
        consents.grant(caller.accountId(), request.purpose(), request.termsVersionId(),
                context.evidenceOf(http));
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * Withdraws a consent.
     *
     * @param purpose purpose to withdraw
     * @return no content
     */
    @DeleteMapping(IdentityRoutes.CONSENTS + "/{purpose}")
    @Operation(summary = "Withdraws the consent in force for one purpose",
            description = "Nothing is deleted: the record gains a withdrawal timestamp. Withdrawing "
                    + "an essential purpose suspends the account and ends its sessions, in the same "
                    + "transaction. Withdrawing ACADEMIC_RESEARCH has no effect on the service.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Consent withdrawn"),
            @ApiResponse(responseCode = "401", description = "No usable session on the request",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "No consent in force for that purpose",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))})
    public ResponseEntity<Void> revoke(@PathVariable ConsentPurpose purpose) {
        AuthenticatedAccount caller = CurrentAccount.require();
        consents.revoke(caller.accountId(), purpose);
        return ResponseEntity.noContent().build();
    }

    /**
     * Reaffirms consent in the holder's own name.
     *
     * @param request the token, when the caller is not signed in
     * @param http    current request, for the evidence
     * @return no content
     */
    @PostMapping(value = IdentityRoutes.CONSENT_REAFFIRMATION, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Reaffirms consent after reaching the consent age",
            description = "Writes a new consent record in the holder's own name and withdraws the "
                    + "one a guardian had granted, in one transaction. The previous record is "
                    + "preserved. Either a token or a session identifies the holder.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Consent reaffirmed"),
            @ApiResponse(responseCode = "401", description = "Neither a token nor a usable session",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Token unknown, expired or already used",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "The account is in a state that cannot "
                    + "reaffirm, or the holder has not reached the consent age",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))})
    public ResponseEntity<Void> reaffirm(@Valid @RequestBody ReaffirmationRequest request,
            HttpServletRequest http) {

        if (request.token() != null && !request.token().isBlank()) {
            reaffirmation.reaffirmWithToken(request.token(), context.evidenceOf(http));
        } else {
            reaffirmation.reaffirm(CurrentAccount.require().accountId(), context.evidenceOf(http));
        }
        return ResponseEntity.noContent().build();
    }

    private static TermsVersionSummary summaryOf(TermsVersion wording) {
        return new TermsVersionSummary(wording.id(), wording.purpose(), wording.version(),
                wording.body(), wording.publishedAt());
    }
}
