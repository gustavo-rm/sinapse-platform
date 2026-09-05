package br.com.sinapse.platform.shared.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Attaches a correlation identifier to every request.
 *
 * <p>The identifier is placed in the logging context, so that the structured logs of a
 * single request can be joined, and echoed back in the response header so that a client
 * or an operator can quote it in a report. It is the identifier used in place of the
 * data that must not be logged.
 *
 * <p>Registered with the highest precedence so that it also covers requests rejected by
 * the security chain or by rate limiting.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    /** Header carrying the identifier, in and out. */
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    /** Key under which the identifier is published to the logging context. */
    public static final String CORRELATION_ID_MDC_KEY = "correlationId";

    /**
     * An inbound identifier is echoed into logs, so it is accepted only in this shape.
     * Anything else is replaced, which removes log injection and unbounded values.
     */
    private static final Pattern ACCEPTABLE = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String correlationId = resolve(request.getHeader(CORRELATION_ID_HEADER));
        MDC.put(CORRELATION_ID_MDC_KEY, correlationId);
        response.setHeader(CORRELATION_ID_HEADER, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(CORRELATION_ID_MDC_KEY);
        }
    }

    private static String resolve(String inbound) {
        if (inbound != null && ACCEPTABLE.matcher(inbound).matches()) {
            return inbound;
        }
        return UUID.randomUUID().toString();
    }
}
