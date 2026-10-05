package br.com.sinapse.platform.coreclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import br.com.sinapse.platform.coreclient.api.CoreProtocolException;
import br.com.sinapse.platform.coreclient.api.SinapseCore;
import br.com.sinapse.platform.coreclient.contract.PlanRequest;
import br.com.sinapse.platform.coreclient.contract.PlanResponse;
import br.com.sinapse.platform.coreclient.contract.SessionKind;
import br.com.sinapse.platform.coreclient.internal.CoreProperties;
import br.com.sinapse.platform.coreclient.internal.RestSinapseCore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * The invariants of a core answer that are checked against the request it answers.
 *
 * <p>Each violation case breaks exactly one invariant and leaves every other one satisfied, so
 * that a refusal can only be the check under test. The request is fixed: one evening window,
 * two topics.
 */
class CoreResponseInvariantsTest {

    private static final long SEED = 20261005L;

    private static final UUID TOPIC_A = UUID.fromString("a0000001-0000-4000-8000-000000000001");

    private static final UUID TOPIC_B = UUID.fromString("a0000002-0000-4000-8000-000000000002");

    /** The window of the audit case: 22:00 to midnight UTC. */
    private static final Instant WINDOW_START = Instant.parse("2026-10-05T22:00:00Z");

    private static final Instant WINDOW_END = Instant.parse("2026-10-06T00:00:00Z");

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private MockRestServiceServer server;
    private SinapseCore core;

    @BeforeEach
    void bindClient() {
        MappingJackson2HttpMessageConverter converter =
                new MappingJackson2HttpMessageConverter(objectMapper);
        RestClient.Builder builder = RestClient.builder().baseUrl("http://core.invalid")
                .messageConverters(converters -> converters.add(0, converter));
        server = MockRestServiceServer.bindTo(builder).build();
        core = new RestSinapseCore(builder.build(), new CoreProperties("http://core.invalid",
                "/plans", Duration.ofSeconds(1), Duration.ofSeconds(1)));
    }

    @Test
    void aScheduleThatKeepsEveryInvariantIsAccepted() throws Exception {
        PlanResponse response = answer(request(window(WINDOW_START, WINDOW_END)),
                session(0, TOPIC_A, "2026-10-05T22:00:00Z", 50),
                session(1, TOPIC_B, "2026-10-05T23:00:00Z", 30));

        assertThat(response.sessions()).hasSize(2);
    }

    /**
     * V3, the audit case: the session starts inside the window and ends twenty minutes after it.
     */
    @Test
    void v3ASessionThatStartsInsideTheWindowAndEndsOutsideIsRefused() throws Exception {
        PlanRequest request = request(window(WINDOW_START, WINDOW_END));
        expect(response(session(0, TOPIC_A, "2026-10-05T23:50:00Z", 30)));

        assertThatThrownBy(() -> core.generate(request))
                .isInstanceOf(CoreProtocolException.class)
                .hasMessageContaining("session 0")
                .hasMessageContaining(TOPIC_A.toString())
                .hasMessageContaining("window");
    }

    private PlanResponse answer(PlanRequest request, PlanResponse.ScheduledSession... sessions)
            throws Exception {
        expect(response(sessions));
        return core.generate(request);
    }

    private void expect(PlanResponse response) throws Exception {
        server.expect(requestTo("http://core.invalid/plans"))
                .andRespond(withSuccess(objectMapper.writeValueAsString(response),
                        MediaType.APPLICATION_JSON));
    }

    private static PlanRequest request(PlanRequest.AvailabilitySlot... windows) {
        return new PlanRequest(PlanRequest.VERSION,
                new PlanRequest.Horizon(LocalDate.parse("2026-10-05"),
                        LocalDate.parse("2026-11-02")),
                List.of(windows),
                List.of(new PlanRequest.Goal(UUID.randomUUID(), null, 3)),
                List.of(new PlanRequest.Topic(TOPIC_A, UUID.randomUUID(), 1, "STANDARD", 50),
                        new PlanRequest.Topic(TOPIC_B, UUID.randomUUID(), 2, "SHORT", 25)),
                List.of(),
                List.of(),
                Map.of("generations", 3),
                SEED);
    }

    private static PlanRequest.AvailabilitySlot window(Instant start, Instant end) {
        return new PlanRequest.AvailabilitySlot(start, end);
    }

    private static PlanResponse response(PlanResponse.ScheduledSession... sessions) {
        return new PlanResponse(PlanRequest.VERSION, List.of(sessions), Map.of("coverage", 1.0),
                new PlanResponse.ExecutionMetadata("core-test-1.0", SEED, 10, 5));
    }

    private static PlanResponse.ScheduledSession session(int sequenceIndex, UUID topic,
            String start, int minutes) {
        return new PlanResponse.ScheduledSession(topic, SessionKind.STUDY, Instant.parse(start),
                minutes, sequenceIndex);
    }
}
