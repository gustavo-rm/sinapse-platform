package br.com.sinapse.platform.shared.web.problem;

import io.swagger.v3.oas.annotations.media.Schema;
import java.net.URI;
import java.util.List;

/**
 * The error body as it travels, for the API description and nothing else.
 *
 * <p>Error bodies are built by {@link ProblemDetails} as Spring's {@code ProblemDetail}, whose own
 * schema does not describe the wire: Jackson writes its {@code properties} map flattened into the
 * top level, so springdoc's schema for it declares a {@code properties} member no body carries and
 * leaves out the {@code errors} member every validation failure sends. A client generated from
 * that schema cannot read the offending fields without a cast. Error responses therefore
 * reference this type, which declares exactly the members the bodies hold.
 *
 * <p>An interface because nothing ever implements it: no body is built from it, so it has no
 * code to run. {@code OpenApiProblemSchemaIntegrationTest} sends each kind of error the platform
 * emits and holds the real bodies against the schema generated from here, so the two cannot
 * drift apart unnoticed.
 */
@Schema(name = "ProblemDetails",
        description = "RFC 7807 error body, served as application/problem+json")
public interface ProblemDetailsSchema {

    /** @return the stable identifier of the error type */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
            description = "Stable identifier of the error type, urn:sinapse:problem:<code>; "
                    + "a client tells errors apart by this member",
            example = "urn:sinapse:problem:resource-not-found")
    URI getType();

    /** @return the short summary of the error type */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
            description = "Short summary of the error type, the same on every occurrence")
    String getTitle();

    /** @return the status of the response */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
            description = "HTTP status of the response")
    int getStatus();

    /** @return the explanation written for the error type */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
            description = "Fixed explanation of the error type; it never quotes the request")
    String getDetail();

    /** @return the path of the request that failed */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
            description = "Path of the request that failed, without the query string")
    URI getInstance();

    /** @return the offending fields of a validation failure */
    @Schema(description = "Only when type is urn:sinapse:problem:validation-failed: the "
            + "offending fields, ordered by field and then by message")
    List<ValidationError> getErrors();
}
