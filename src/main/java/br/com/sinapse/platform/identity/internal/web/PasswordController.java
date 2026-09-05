package br.com.sinapse.platform.identity.internal.web;

import br.com.sinapse.platform.identity.internal.security.CurrentAccount;
import br.com.sinapse.platform.identity.internal.service.PasswordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Setting a new password, with a token or with the current one.
 *
 * <p>Every one of these ends every session of the account, including the one that asked. The
 * case that decides it is the one where the password is being changed because somebody else
 * knows it.
 */
@RestController
@Tag(name = "Passwords", description = "Reset and change of a password")
public class PasswordController {

    private final PasswordService passwords;

    /**
     * @param passwords password use cases
     */
    public PasswordController(PasswordService passwords) {
        this.passwords = passwords;
    }

    /**
     * Asks for a reset link.
     *
     * @param request the address
     * @return no content, whether or not the address is registered
     */
    @PostMapping(value = IdentityRoutes.PASSWORD_RESETS, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Requests a password reset",
            description = "Answers the same way whether or not the address is registered. The "
                    + "alternative turns this route into a way of testing who has an account here.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Request accepted"),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))})
    public ResponseEntity<Void> requestReset(@Valid @RequestBody PasswordResetRequest request) {
        passwords.requestReset(request.email().trim());
        return ResponseEntity.noContent().build();
    }

    /**
     * Sets a new password against a reset token.
     *
     * @param request token and new password
     * @return no content
     */
    @PostMapping(value = IdentityRoutes.PASSWORD_RESET_CONFIRMATION,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Completes a password reset",
            description = "Single use. Every session of the account is ended in the same transaction.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Password changed and sessions ended"),
            @ApiResponse(responseCode = "404", description = "Token unknown, expired or already used",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))})
    public ResponseEntity<Void> completeReset(@Valid @RequestBody PasswordResetConfirmation request) {
        passwords.completeReset(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    /**
     * Changes the caller's password.
     *
     * @param request current and new password
     * @return no content
     */
    @PostMapping(value = IdentityRoutes.PASSWORD_CHANGES, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Changes the caller's password",
            description = "Every session of the account is ended, including the one that asked, so "
                    + "the client authenticates again with the password it just chose.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Password changed and sessions ended"),
            @ApiResponse(responseCode = "401", description = "No usable session, or the current "
                    + "password does not match",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))})
    public ResponseEntity<Void> change(@Valid @RequestBody PasswordChangeRequest request) {
        passwords.change(CurrentAccount.require().accountId(),
                request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }
}
