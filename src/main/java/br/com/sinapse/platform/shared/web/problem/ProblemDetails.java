package br.com.sinapse.platform.shared.web.problem;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/**
 * The only way to build an error body in this application.
 *
 * <p>Every field of the resulting {@link ProblemDetail} comes either from the
 * {@link ApiErrorType} catalogue or from the request path. Nothing here accepts free
 * text, so there is no code path through which an exception message, a field value or
 * a column name can reach a response.
 */
public final class ProblemDetails {

    private ProblemDetails() {
    }

    /**
     * Builds the body for a catalogue entry, answered with the entry's own status.
     *
     * @param errorType catalogue entry
     * @param instance  path of the request that failed, or {@code null}
     * @return the problem body
     */
    public static ProblemDetail of(ApiErrorType errorType, URI instance) {
        return of(errorType, errorType.status(), instance);
    }

    /**
     * Builds the body for a catalogue entry answered with a status resolved elsewhere,
     * which is the case for exceptions Spring itself translates.
     *
     * @param errorType catalogue entry
     * @param status    status of the response
     * @param instance  path of the request that failed, or {@code null}
     * @return the problem body
     */
    public static ProblemDetail of(ApiErrorType errorType, HttpStatusCode status, URI instance) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setType(errorType.type());
        problem.setTitle(errorType.title());
        problem.setDetail(errorType.detail());
        if (instance != null) {
            problem.setInstance(instance);
        }
        return problem;
    }

    /**
     * Adds the {@code errors} array of a validation failure.
     *
     * @param problem body being assembled
     * @param errors  offending fields and their constraint messages
     */
    public static void addValidationErrors(ProblemDetail problem, List<ValidationError> errors) {
        problem.setProperty("errors", errors);
    }

    /**
     * The {@code instance} member: the path of the request, without the query string.
     *
     * <p>The query string is dropped because it may carry values submitted by the user.
     *
     * @param request current request
     * @return the path as a URI
     */
    public static URI instanceOf(HttpServletRequest request) {
        return URI.create(request.getRequestURI());
    }
}
