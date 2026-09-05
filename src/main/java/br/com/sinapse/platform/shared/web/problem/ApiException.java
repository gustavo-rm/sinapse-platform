package br.com.sinapse.platform.shared.web.problem;

/**
 * Base of the exceptions the application answers with a curated error body.
 *
 * <p>The exception carries an {@link ApiErrorType}, not a message: the response text
 * comes from the catalogue. Any message given here stays in the exception, is used
 * for the stack trace during development and is never serialised.
 */
public class ApiException extends RuntimeException {

    private final transient ApiErrorType errorType;

    /**
     * @param errorType catalogue entry that describes the failure to the client
     */
    public ApiException(ApiErrorType errorType) {
        super(errorType.name());
        this.errorType = errorType;
    }

    /**
     * @param errorType catalogue entry that describes the failure to the client
     * @param cause     underlying failure, kept for logging only
     */
    public ApiException(ApiErrorType errorType, Throwable cause) {
        super(errorType.name(), cause);
        this.errorType = errorType;
    }

    /** Catalogue entry that determines the status and the body of the response. */
    public ApiErrorType errorType() {
        return errorType;
    }
}
