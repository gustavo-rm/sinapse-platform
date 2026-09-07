package br.com.sinapse.platform.readmodel.internal.error;

import br.com.sinapse.platform.shared.web.problem.ApiErrorType;
import br.com.sinapse.platform.shared.web.problem.ApiException;

/**
 * Raised when the requested window is empty, inverted or wider than these reads will answer.
 *
 * <p>Refused rather than silently narrowed. A server that trimmed the window would answer a
 * question nobody asked, and the client would present the result as though it covered the
 * period it requested.
 */
public class InvalidReadWindowException extends ApiException {

    /** Creates the failure. */
    public InvalidReadWindowException() {
        super(ApiErrorType.TIME_WINDOW_INVALID);
    }
}
