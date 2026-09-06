package br.com.sinapse.platform.identity.internal.web;

import br.com.sinapse.platform.identity.internal.config.IdentityProperties;
import br.com.sinapse.platform.identity.internal.security.AuthenticatedAccount;
import br.com.sinapse.platform.identity.internal.security.CurrentAccount;
import br.com.sinapse.platform.identity.internal.security.SessionCookies;
import br.com.sinapse.platform.identity.internal.service.AuthenticationService;
import br.com.sinapse.platform.identity.internal.service.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Opening, listing and ending sessions.
 *
 * <p>The holder can see their own sessions and end any of them, which is what makes a
 * suspicion actionable: the list is the only way someone finds out that a credential of
 * theirs is in use somewhere they do not recognise.
 */
@RestController
@Tag(name = "Sessions", description = "Authentication and the caller's own sessions")
public class SessionController {

    private final AuthenticationService authentication;
    private final SessionService sessions;
    private final IdentityProperties properties;
    private final RequestContext context;

    /**
     * @param authentication authentication use case
     * @param sessions       session store
     * @param properties     configured cookie attributes and lifetimes
     * @param context        resolution of the address and agent of the request
     */
    public SessionController(AuthenticationService authentication, SessionService sessions,
            IdentityProperties properties, RequestContext context) {
        this.authentication = authentication;
        this.sessions = sessions;
        this.properties = properties;
        this.context = context;
    }

    /**
     * Authenticates and opens a session.
     *
     * @param request credential submitted
     * @param http    current request
     * @return the session, in the body and in a cookie
     */
    @PostMapping(value = IdentityRoutes.SESSIONS,
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Opens a session",
            description = "Answers with an opaque token, both as an HttpOnly, Secure, "
                    + "SameSite=Strict cookie for browsers and in the body for clients that send "
                    + "it back as Authorization: Bearer. Not a JWT: an account suspended by a "
                    + "consent revocation has to stop working on the next request, not at the "
                    + "next expiry.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Session opened"),
            @ApiResponse(responseCode = "401", description = "Credential refused. The same answer is "
                    + "given for an unknown address, a wrong password and an account that is not active",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))})
    public ResponseEntity<SessionResponse> open(@Valid @RequestBody LoginRequest request,
            HttpServletRequest http) {

        SessionService.IssuedSession issued = authentication.authenticate(
                request.email().trim(), request.password(),
                context.addressOf(http), context.userAgentOf(http));

        Duration lifetime = properties.session().absoluteTimeout();
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE,
                        SessionCookies.issue(properties.session(), issued.token(), lifetime).toString())
                .body(new SessionResponse(issued.session().id(), issued.token(),
                        issued.session().absoluteExpiresAt()));
    }

    /**
     * Lists the caller's usable sessions.
     *
     * @return the sessions, newest first
     */
    @GetMapping(value = IdentityRoutes.SESSIONS, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Lists the caller's own sessions")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The sessions still usable"),
            @ApiResponse(responseCode = "401", description = "No usable session on the request",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))})
    public List<SessionSummary> list() {
        AuthenticatedAccount caller = CurrentAccount.require();
        return sessions.listUsable(caller.accountId()).stream()
                .map(session -> new SessionSummary(
                        session.id(),
                        session.createdAt(),
                        session.lastSeenAt(),
                        session.absoluteExpiresAt(),
                        session.userAgent(),
                        session.id().equals(caller.sessionId())))
                .toList();
    }

    /**
     * Ends the session the request arrived on.
     *
     * @return no content, and a cookie that expires immediately
     */
    @DeleteMapping(IdentityRoutes.SESSIONS + "/current")
    @Operation(summary = "Ends the session the request arrived on")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Session ended"),
            @ApiResponse(responseCode = "401", description = "No usable session on the request",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))})
    public ResponseEntity<Void> endCurrent() {
        AuthenticatedAccount caller = CurrentAccount.require();
        sessions.terminate(caller.accountId(), caller.sessionId());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, SessionCookies.clear(properties.session()).toString())
                .build();
    }

    /**
     * Ends one of the caller's sessions.
     *
     * @param sessionId session to end
     * @return no content
     */
    @DeleteMapping(IdentityRoutes.SESSIONS + "/{sessionId}")
    @Operation(summary = "Ends one of the caller's sessions",
            description = "A session of another account answers the same way as one that does not "
                    + "exist, so that the route cannot be used to discover somebody else's sessions.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Session ended"),
            @ApiResponse(responseCode = "401", description = "No usable session on the request",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "No such session for this caller",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))})
    public ResponseEntity<Void> end(@PathVariable UUID sessionId) {
        AuthenticatedAccount caller = CurrentAccount.require();
        sessions.terminate(caller.accountId(), sessionId);
        return ResponseEntity.noContent().build();
    }
}
