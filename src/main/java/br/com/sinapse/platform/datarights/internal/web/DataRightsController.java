package br.com.sinapse.platform.datarights.internal.web;

import br.com.sinapse.platform.datarights.api.ErasureRequestView;
import br.com.sinapse.platform.datarights.api.TeacherAccessPeriod;
import br.com.sinapse.platform.datarights.internal.service.AccessDisclosureService;
import br.com.sinapse.platform.datarights.internal.service.ErasureRequestService;
import br.com.sinapse.platform.datarights.internal.service.PersonalDataExportService;
import br.com.sinapse.platform.identity.api.CurrentAccount;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The rights of Article 18 that this platform answers: access, erasure, and information about
 * who the holder's data was shared with.
 *
 * <p>Correction is not here. It is already answered by the ordinary endpoints of each module —
 * a holder corrects their availability by declaring it again, and their goals by revising them.
 *
 * <p><strong>None of these routes takes an account identifier.</strong> Every one acts on the
 * caller, which is what makes it impossible to write one that erases or exports somebody else.
 *
 * <p>They stay reachable while the account is suspended, which is the point of the seven-day
 * window: a holder who has asked to be erased has to be able to change their mind, and one who
 * has withdrawn their consent has more reason to want their own data than anyone.
 */
@RestController
@Tag(name = "Data subject rights", description = "Access, erasure, and who has seen your data")
public class DataRightsController {

    private final ErasureRequestService requests;
    private final PersonalDataExportService exports;
    private final AccessDisclosureService disclosure;

    /**
     * @param requests   the erasure request lifecycle
     * @param exports    assembly of the holder's own data
     * @param disclosure who could read it, and when
     */
    public DataRightsController(ErasureRequestService requests, PersonalDataExportService exports,
            AccessDisclosureService disclosure) {
        this.requests = requests;
        this.exports = exports;
        this.disclosure = disclosure;
    }

    /**
     * Asks for the account and its data to be erased.
     *
     * @return the request, which can be withdrawn until it takes effect
     */
    @PostMapping(value = DataRightsRoutes.ERASURE_REQUESTS,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Asks for this account to be erased",
            description = "The account is suspended and its sessions revoked at once; the "
                    + "erasure itself happens after a configured window, and can be withdrawn "
                    + "until then. Signing in still works during the window, and does nothing "
                    + "but let the holder withdraw the request or take their data.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Erasure requested"),
            @ApiResponse(responseCode = "409", description = "There is already an open request",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))})
    public ResponseEntity<ErasureRequestView> request() {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(requests.request(callerId()));
    }

    /**
     * The caller's erasure requests.
     *
     * @return every request they have made, newest first
     */
    @GetMapping(value = DataRightsRoutes.ERASURE_REQUESTS,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Lists the caller's erasure requests",
            description = "Including withdrawn and failed ones. None of them carries a copy of "
                    + "anything that was erased.")
    @ApiResponse(responseCode = "200", description = "The requests")
    public List<ErasureRequestView> list() {
        return requests.requestsOf(callerId());
    }

    /**
     * Withdraws an open request.
     *
     * @param requestId request to withdraw
     * @return the withdrawn request
     */
    @PostMapping(value = DataRightsRoutes.ERASURE_REQUESTS + "/{requestId}/cancellation",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Withdraws an erasure request",
            description = "Puts the account back to active, provided its essential consent is "
                    + "still valid. After the window has elapsed there is nothing to withdraw.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Request withdrawn"),
            @ApiResponse(responseCode = "404", description = "No such request for this account",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "The request has already been "
                    + "carried out, withdrawn or failed",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))})
    public ErasureRequestView cancel(@PathVariable UUID requestId) {
        return requests.cancel(callerId(), requestId);
    }

    /**
     * Everything the platform holds about the caller.
     *
     * @return the assembled document, as a download
     */
    @GetMapping(value = DataRightsRoutes.DATA_EXPORT, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Downloads everything the platform holds about the caller",
            description = "Assembled from each module, keyed by module. Delivered through this "
                    + "authenticated download and never by e-mail: a mailbox is not a place to "
                    + "put somebody's whole study history. Rate limited, because assembling it "
                    + "reads every table that mentions the holder.")
    @ApiResponse(responseCode = "200", description = "The holder's data")
    @ApiResponse(responseCode = "429", description = "Rate limit exceeded",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<PersonalDataExportService.PersonalDataExport> export() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("sinapse-data-export.json")
                        .build()
                        .toString())
                .body(exports.exportFor(callerId()));
    }

    /**
     * Who could read the caller's data, and when.
     *
     * @return one period per membership, newest first
     */
    @GetMapping(value = DataRightsRoutes.ACCESS_DISCLOSURE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Lists who could read the caller's data, and when",
            description = "Derived from the memberships, because a membership is the only thing "
                    + "that grants a teacher access. A period is an upper bound: access also "
                    + "requires a valid INSTITUTION_SHARING consent at the moment of each read, "
                    + "and withdrawing that cuts it off without ending the membership.")
    @ApiResponse(responseCode = "200", description = "The periods")
    public List<TeacherAccessPeriod> accessDisclosure() {
        return disclosure.disclosureFor(callerId());
    }

    private static UUID callerId() {
        return CurrentAccount.require().accountId();
    }
}
