package br.com.sinapse.platform.shared.observability;

import static br.com.sinapse.platform.shared.observability.CorrelationIdFilter.CORRELATION_ID_HEADER;
import static br.com.sinapse.platform.shared.observability.CorrelationIdFilter.CORRELATION_ID_MDC_KEY;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * The correlation identifier is what stands in for the data that must never be logged, so
 * it has to be present during the request, gone afterwards, and never under the client's
 * control beyond a shape that is safe to echo.
 */
class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void clearContext() {
        MDC.clear();
    }

    @Test
    void generatesAnIdentifierAndEchoesIt() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request(), response, capturingChain());

        String echoed = response.getHeader(CORRELATION_ID_HEADER);
        assertThat(echoed).isNotNull();
        assertThat(UUID.fromString(echoed)).isNotNull();
        assertThat(observedCorrelationId).isEqualTo(echoed);
    }

    @Test
    void acceptsAnInboundIdentifierSoThatCallsCanBeJoinedAcrossSystems() throws Exception {
        MockHttpServletRequest request = request();
        request.addHeader(CORRELATION_ID_HEADER, "checkout-7f3a_9");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, capturingChain());

        assertThat(response.getHeader(CORRELATION_ID_HEADER)).isEqualTo("checkout-7f3a_9");
        assertThat(observedCorrelationId).isEqualTo("checkout-7f3a_9");
    }

    @Test
    void replacesAnInboundIdentifierThatCouldForgeALogLine() throws Exception {
        MockHttpServletRequest request = request();
        request.addHeader(CORRELATION_ID_HEADER, "abc\n{\"level\":\"ERROR\"}");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, capturingChain());

        assertThat(response.getHeader(CORRELATION_ID_HEADER)).doesNotContain("ERROR");
        assertThat(UUID.fromString(response.getHeader(CORRELATION_ID_HEADER))).isNotNull();
    }

    @Test
    void replacesAnUnboundedInboundIdentifier() throws Exception {
        MockHttpServletRequest request = request();
        request.addHeader(CORRELATION_ID_HEADER, "a".repeat(65));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, capturingChain());

        assertThat(response.getHeader(CORRELATION_ID_HEADER)).hasSize(36);
    }

    @Test
    void leavesNothingBehindInTheLoggingContext() throws Exception {
        filter.doFilter(request(), new MockHttpServletResponse(), capturingChain());

        assertThat(MDC.get(CORRELATION_ID_MDC_KEY))
                .as("a leaked entry would tag the next request handled by this thread")
                .isNull();
    }

    private String observedCorrelationId;

    private FilterChain capturingChain() {
        return (request, response) -> observedCorrelationId = MDC.get(CORRELATION_ID_MDC_KEY);
    }

    private static MockHttpServletRequest request() {
        return new MockHttpServletRequest("GET", "/api/v1/probe/ping");
    }
}
