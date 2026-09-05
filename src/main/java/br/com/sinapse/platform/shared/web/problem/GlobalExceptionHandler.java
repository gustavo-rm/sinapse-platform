package br.com.sinapse.platform.shared.web.problem;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Single point where an exception becomes an HTTP response.
 *
 * <p>It extends {@link ResponseEntityExceptionHandler} because that is what routes
 * Spring's own exceptions through here as well. Without it, exceptions such as
 * {@code HttpMessageNotReadableException} escape to the container, and their messages —
 * which quote the offending fragment of the request body — end up in the log and in
 * the response.
 *
 * <p>{@link #handleExceptionInternal} is overridden to <em>discard</em> whatever body
 * the framework assembled and rebuild it from {@link ApiErrorType}. That single
 * override is what makes ADR 0009 structural: a handler added later cannot leak,
 * because it has no way of writing its own text.
 *
 * <p>Logging follows the same rule. Only the exception type, the resolved status and
 * the route template are logged; never the message of a client-side failure, never
 * the request body, never a parameter value.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Package whose frames identify where in this application a failure originated. */
    private static final String APPLICATION_PACKAGE = "br.com.sinapse.platform";

    /** Bound on the cause chain walked when describing a failure. */
    private static final int MAX_CAUSE_DEPTH = 5;

    /**
     * Rebuilds every response produced by the framework's own handlers.
     *
     * @param ex         exception being handled
     * @param body       body assembled by Spring, discarded on purpose
     * @param headers    headers proposed by Spring
     * @param statusCode status Spring resolved
     * @param request    current request
     * @return the sanitised response
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object body,
            HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {

        ApiErrorType errorType = errorTypeOf(ex, statusCode);
        ProblemDetail problem = ProblemDetails.of(errorType, statusCode, instanceOf(request));

        List<ValidationError> validationErrors = validationErrorsOf(ex);
        if (!validationErrors.isEmpty()) {
            ProblemDetails.addValidationErrors(problem, validationErrors);
        }

        log(ex, statusCode);
        return super.handleExceptionInternal(ex, problem, problemHeaders(headers), statusCode, request);
    }

    /**
     * Handles the exceptions the application raises deliberately.
     *
     * @param ex      exception carrying the catalogue entry
     * @param request current request
     * @return the response described by the catalogue entry
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Object> handleApiException(ApiException ex, WebRequest request) {
        return handleExceptionInternal(ex, null, new HttpHeaders(), ex.errorType().status(), request);
    }

    /**
     * Last resort for anything that reached the dispatcher unhandled.
     *
     * <p>The exception is logged with its stack trace, on the server; the client gets
     * {@link ApiErrorType#INTERNAL_ERROR} and nothing else.
     *
     * @param ex      unanticipated exception
     * @param request current request
     * @return a 500 response with a curated body
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        return handleExceptionInternal(ex, null, new HttpHeaders(),
                ApiErrorType.INTERNAL_ERROR.status(), request);
    }

    /**
     * Chooses the catalogue entry for an exception.
     *
     * <p>Deliberately the only place where an exception influences the response: it picks
     * an entry, and the entry supplies every word the client will read.
     */
    private static ApiErrorType errorTypeOf(Exception ex, HttpStatusCode statusCode) {
        if (ex instanceof ApiException apiException) {
            return apiException.errorType();
        }
        // MethodArgumentNotValidException is a BindException; both carry a BindingResult.
        if (ex instanceof BindException || ex instanceof HandlerMethodValidationException) {
            return ApiErrorType.VALIDATION_FAILED;
        }
        return ApiErrorType.forStatus(statusCode);
    }

    private static List<ValidationError> validationErrorsOf(Exception ex) {
        if (ex instanceof BindException bindException) {
            return ValidationError.from(bindException.getBindingResult());
        }
        if (ex instanceof HandlerMethodValidationException methodValidation) {
            return methodValidation.getParameterValidationResults().stream()
                    .flatMap(result -> result.getResolvableErrors().stream()
                            .map(error -> new ValidationError(
                                    parameterNameOf(result.getMethodParameter().getParameterName()),
                                    error.getDefaultMessage() == null ? "invalid" : error.getDefaultMessage())))
                    .sorted(java.util.Comparator.comparing(ValidationError::field)
                            .thenComparing(ValidationError::message))
                    .toList();
        }
        return List.of();
    }

    private static String parameterNameOf(@Nullable String parameterName) {
        return parameterName == null ? "request" : parameterName;
    }

    private static HttpHeaders problemHeaders(HttpHeaders source) {
        HttpHeaders headers = new HttpHeaders();
        source.forEach(headers::addAll);
        headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return headers;
    }

    @Nullable
    private static URI instanceOf(WebRequest request) {
        if (request instanceof ServletWebRequest servletWebRequest) {
            HttpServletRequest servletRequest = servletWebRequest.getRequest();
            return ProblemDetails.instanceOf(servletRequest);
        }
        return null;
    }

    /**
     * Logs the failure without any request content.
     *
     * <p>The exception is never handed to the logger as an exception, which would print
     * its message and the messages of its causes. Those messages routinely quote the value
     * that caused the failure — the address that failed to parse, the row that violated a
     * constraint — and that value is precisely what may be personal data. What is logged
     * instead is the chain of types and where in this application each link originated,
     * which is what a diagnosis actually needs. The request is identified by the
     * correlation id carried in the logging context.
     */
    private static void log(Exception ex, HttpStatusCode statusCode) {
        if (statusCode.is5xxServerError()) {
            LOG.error("Request failed with status {}: {}", statusCode.value(), describe(ex));
        } else if (LOG.isDebugEnabled()) {
            LOG.debug("Request rejected with status {}: {}", statusCode.value(), describe(ex));
        }
    }

    /**
     * Renders a failure as its chain of exception types and the frame each one came from.
     *
     * @param throwable failure to describe
     * @return a single line naming types and code locations, and nothing else
     */
    private static String describe(Throwable throwable) {
        StringBuilder description = new StringBuilder();
        Throwable current = throwable;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (depth > 0) {
                description.append(" caused by ");
            }
            description.append(current.getClass().getName());
            StackTraceElement origin = originOf(current);
            if (origin != null) {
                description.append('(').append(origin.getClassName()).append('.')
                        .append(origin.getMethodName()).append(':').append(origin.getLineNumber())
                        .append(')');
            }
            current = current.getCause() == current ? null : current.getCause();
        }
        return description.toString();
    }

    /**
     * The frame of this application a failure came from, falling back to the topmost frame
     * when the failure never passed through application code. Class, method and line names
     * are code identifiers, never data.
     */
    @Nullable
    private static StackTraceElement originOf(Throwable throwable) {
        StackTraceElement[] frames = throwable.getStackTrace();
        for (StackTraceElement frame : frames) {
            if (frame.getClassName().startsWith(APPLICATION_PACKAGE)) {
                return frame;
            }
        }
        return frames.length == 0 ? null : frames[0];
    }
}
