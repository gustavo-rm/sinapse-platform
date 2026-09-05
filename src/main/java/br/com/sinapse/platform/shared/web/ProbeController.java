package br.com.sinapse.platform.shared.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Clock;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Diagnostic routes that exercise the cross-cutting decisions end to end.
 *
 * <p>They exist so that the error contract, the rate limiter and the time serialisation
 * can be asserted against a running chain while no domain endpoint exists yet. One of
 * them fails on purpose, so the controller is restricted to the {@code local} and
 * {@code test} profiles and is absent from any other deployment.
 */
@RestController
@RequestMapping(ApiPaths.V1 + "/probe")
@Profile({"local", "test"})
@Tag(name = "Probe", description = "Diagnostic routes. Not available outside the local and test profiles.")
public class ProbeController {

    private final Clock clock;

    /**
     * @param clock application clock; the probe never reads the system clock directly
     */
    public ProbeController(Clock clock) {
        this.clock = clock;
    }

    /**
     * Confirms that the chain is reachable and shows how instants are serialised.
     *
     * @return a fixed acknowledgement and the current instant
     */
    @GetMapping(value = "/ping", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Confirms that the API is answering")
    @ApiResponse(responseCode = "200", description = "The API is answering")
    public ProbeResponse ping() {
        return new ProbeResponse("ok", clock.instant());
    }

    /**
     * Fails with an exception whose message deliberately carries an e-mail address and an
     * entity name, to prove that neither reaches the response or the log.
     *
     * @return never returns
     */
    @GetMapping(value = "/failure", produces = MediaType.APPLICATION_PROBLEM_JSON_VALUE)
    @Operation(summary = "Fails on purpose, to exercise the RFC 7807 contract")
    @ApiResponse(responseCode = "500", description = "Curated error body",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    public ProbeResponse failure() {
        throw new IllegalStateException(
                "Account aluno@example.com not found in table identity_account, column email_hash");
    }

    /**
     * Validates a body, to exercise the {@code errors} array of the error contract.
     *
     * @param request body to validate
     * @return a fixed acknowledgement when the body is valid
     */
    @PostMapping(value = "/validation", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Validates a body, to exercise the errors array")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Body accepted"),
            @ApiResponse(responseCode = "400", description = "Body rejected, with the offending fields",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))})
    public ProbeResponse validation(@Valid @RequestBody ProbeRequest request) {
        return new ProbeResponse("ok", clock.instant());
    }

    /**
     * Route covered by a rate limit policy, so that the limiter can be exercised without
     * a domain endpoint.
     *
     * @return a fixed acknowledgement while the window has room
     */
    @GetMapping(value = "/rate-limited", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Route governed by a rate limit policy")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Within the window"),
            @ApiResponse(responseCode = "429", description = "Window exhausted",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))})
    public ProbeResponse rateLimited() {
        return new ProbeResponse("ok", clock.instant());
    }
}
