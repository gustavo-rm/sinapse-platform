package br.com.sinapse.platform.shared.web.problem;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * Closed catalogue of error types exposed by the API.
 *
 * <p>Each constant carries a stable {@code type} URI, which acts as the versionable
 * error code, and the exact {@code title} and {@code detail} texts that will be sent
 * to the client. Both texts are written here, once, and reviewed here.
 *
 * <p>This is the mechanism that keeps ADR 0009 enforceable: because a body can only
 * be assembled from a constant of this enum, no {@code detail} can contain personal
 * data, an internal identifier, an entity name, a column name or a query fragment.
 * The texts are deliberately coarse — a client distinguishes cases by {@code type},
 * not by reading prose.
 *
 * <p>Texts are in English. The API does not return display text; translation is the
 * client's responsibility (see {@code docs/CONTRATO_API_SINAPSE.md}).
 */
public enum ApiErrorType {

    /** Syntactically valid request whose fields failed validation. */
    VALIDATION_FAILED("validation-failed", HttpStatus.BAD_REQUEST,
            "Validation failed",
            "One or more fields of the request are invalid. See the errors array."),

    /** Request body or parameters could not be read at all. */
    MALFORMED_REQUEST("malformed-request", HttpStatus.BAD_REQUEST,
            "Malformed request",
            "The request could not be read."),

    /**
     * Time window of a history request that the server will not answer.
     *
     * <p>It has a type of its own because the client's remedy is specific — ask for a
     * narrower interval — and neither "malformed" nor "validation failed" says that. The text
     * states the rule and never the configured maximum: the limit is configuration, the
     * catalogue is not, and a client that needs the number reads it from the API description.
     */
    TIME_WINDOW_INVALID("time-window-invalid", HttpStatus.BAD_REQUEST,
            "Invalid time window",
            "The requested time window must end after it starts and may not be wider than the "
                    + "maximum this API accepts for a history."),

    /** No credentials, or credentials that are no longer valid. */
    UNAUTHENTICATED("unauthenticated", HttpStatus.UNAUTHORIZED,
            "Unauthenticated",
            "Valid authentication is required for this resource."),

    /** Authenticated caller without permission over the resource. */
    ACCESS_DENIED("access-denied", HttpStatus.FORBIDDEN,
            "Access denied",
            "The caller is not allowed to perform this operation."),

    /**
     * Resource absent or invisible to the caller. The two cases share one type on
     * purpose: distinguishing them would disclose the existence of a resource to
     * someone who may not read it.
     */
    RESOURCE_NOT_FOUND("resource-not-found", HttpStatus.NOT_FOUND,
            "Resource not found",
            "The requested resource does not exist or is not available to the caller."),

    /** HTTP method not supported by the route. */
    METHOD_NOT_ALLOWED("method-not-allowed", HttpStatus.METHOD_NOT_ALLOWED,
            "Method not allowed",
            "The HTTP method is not supported by this route."),

    /** No representation acceptable to the client. */
    NOT_ACCEPTABLE("not-acceptable", HttpStatus.NOT_ACCEPTABLE,
            "Not acceptable",
            "No representation available in the media types accepted by the client."),

    /**
     * Registration on behalf of someone below the configured consent age.
     *
     * <p>It has a type of its own because the client has to be able to say why the
     * registration was refused, and the catalogue is the only place a text may come from.
     * The wording states the rule and never the submitted date of birth.
     */
    MINOR_REGISTRATION_NOT_SUPPORTED("minor-registration-not-supported", HttpStatus.UNPROCESSABLE_ENTITY,
            "Registration not available for a minor",
            "This version registers only account holders who are of age. Registration with "
                    + "consent from a guardian is not available yet."),

    /**
     * An operation that would leave the prerequisite graph cyclic.
     *
     * <p>It has a type of its own because a curator needs to know which rule was broken, and
     * the database's own message names two identifiers, which a body may not carry. The text
     * states the rule; where the cycle is stays in the log, and the importer of ADR 0014
     * finds it before the database ever has to.
     */
    PREREQUISITE_CYCLE("prerequisite-cycle", HttpStatus.CONFLICT,
            "Prerequisite cycle",
            "The operation would make a topic a prerequisite of itself, directly or through "
                    + "other topics. The prerequisite graph has to stay acyclic."),

    /**
     * A plan was asked for before there is anything to plan.
     *
     * <p>It has a type of its own because the client's remedy is specific and a generic
     * conflict does not name it: declare availability and at least one goal. Decision F2
     * requires both and nothing else — without them the optimiser has nothing to optimise, and
     * a plan built from defaults would be fiction presented as a recommendation.
     */
    SETUP_INCOMPLETE("setup-incomplete", HttpStatus.CONFLICT,
            "Setup incomplete",
            "A study plan cannot be generated before availability and at least one goal have "
                    + "been declared."),

    /** Request contradicts the current state of the resource. */
    CONFLICT("conflict", HttpStatus.CONFLICT,
            "Conflict",
            "The request conflicts with the current state of the resource."),

    /** Request body larger than the configured limit. */
    PAYLOAD_TOO_LARGE("payload-too-large", HttpStatus.PAYLOAD_TOO_LARGE,
            "Payload too large",
            "The request body exceeds the accepted size."),

    /** Content type not supported by the route. */
    UNSUPPORTED_MEDIA_TYPE("unsupported-media-type", HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            "Unsupported media type",
            "The content type of the request is not supported by this route."),

    /** Rate limit exceeded for the route (ADR 0009). */
    RATE_LIMIT_EXCEEDED("rate-limit-exceeded", HttpStatus.TOO_MANY_REQUESTS,
            "Rate limit exceeded",
            "Too many requests for this route. Retry after the indicated interval."),

    /** Anything the application did not anticipate. */
    INTERNAL_ERROR("internal-error", HttpStatus.INTERNAL_SERVER_ERROR,
            "Internal error",
            "The request could not be completed."),

    /** Dependency unavailable, typically the Sinapse Core. */
    SERVICE_UNAVAILABLE("service-unavailable", HttpStatus.SERVICE_UNAVAILABLE,
            "Service unavailable",
            "The service is temporarily unable to handle the request.");

    /**
     * Namespace of the {@code type} URIs. A URN is used rather than an {@code https}
     * URL so that the error code does not depend on a documentation host being
     * registered, reachable or stable.
     */
    private static final String TYPE_NAMESPACE = "urn:sinapse:problem:";

    private final URI type;
    private final HttpStatus status;
    private final String title;
    private final String detail;

    ApiErrorType(String code, HttpStatus status, String title, String detail) {
        this.type = URI.create(TYPE_NAMESPACE + code);
        this.status = status;
        this.title = title;
        this.detail = detail;
    }

    /** Stable URI that identifies this error type. */
    public URI type() {
        return type;
    }

    /** HTTP status this error type is normally answered with. */
    public HttpStatus status() {
        return status;
    }

    /** Short, human-readable summary of the error type. */
    public String title() {
        return title;
    }

    /** Pre-written explanation. Never composed from request or entity data. */
    public String detail() {
        return detail;
    }

    /**
     * Maps a status code produced elsewhere in the framework onto a catalogue entry.
     *
     * <p>Status alone cannot separate a rejected field from an unreadable body — both are
     * 400 — so the caller refines the choice by exception type. An unmapped status falls
     * back to {@link #INTERNAL_ERROR} for server errors and to {@link #MALFORMED_REQUEST}
     * for client errors, so that a Spring exception this application never anticipated
     * still leaves through a curated text.
     *
     * @param statusCode status Spring resolved for the exception
     * @return the matching catalogue entry, never {@code null}
     */
    public static ApiErrorType forStatus(HttpStatusCode statusCode) {
        return switch (statusCode.value()) {
            case 400 -> MALFORMED_REQUEST;
            case 401 -> UNAUTHENTICATED;
            case 403 -> ACCESS_DENIED;
            case 404 -> RESOURCE_NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 406 -> NOT_ACCEPTABLE;
            case 409 -> CONFLICT;
            case 413 -> PAYLOAD_TOO_LARGE;
            case 415 -> UNSUPPORTED_MEDIA_TYPE;
            case 429 -> RATE_LIMIT_EXCEEDED;
            case 503 -> SERVICE_UNAVAILABLE;
            default -> statusCode.is4xxClientError() ? MALFORMED_REQUEST : INTERNAL_ERROR;
        };
    }
}
