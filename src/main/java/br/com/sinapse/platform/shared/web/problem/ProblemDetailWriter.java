package br.com.sinapse.platform.shared.web.problem;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/**
 * Writes an error body from outside the dispatcher.
 *
 * <p>Servlet filters and the Spring Security entry points run before, or instead of,
 * {@link GlobalExceptionHandler}, so they cannot rely on it. They use this component
 * and therefore produce exactly the same contract, built from the same catalogue.
 */
@Component
public class ProblemDetailWriter {

    private final ObjectMapper objectMapper;

    /**
     * @param objectMapper the application's configured mapper, so that the body is
     *                     serialised the same way the dispatcher would serialise it
     */
    public ProblemDetailWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Writes the body of a catalogue entry to the response and commits the status.
     *
     * @param request   current request, used only for the {@code instance} member
     * @param response  response to write to
     * @param errorType catalogue entry
     * @throws IOException if the response cannot be written
     */
    public void write(HttpServletRequest request, HttpServletResponse response, ApiErrorType errorType)
            throws IOException {
        ProblemDetail problem = ProblemDetails.of(errorType, ProblemDetails.instanceOf(request));
        response.setStatus(errorType.status().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
