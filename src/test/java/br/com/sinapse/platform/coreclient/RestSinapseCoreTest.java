package br.com.sinapse.platform.coreclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import br.com.sinapse.platform.coreclient.api.CoreProtocolException;
import br.com.sinapse.platform.coreclient.api.CoreUnavailableException;
import br.com.sinapse.platform.coreclient.api.SinapseCore;
import br.com.sinapse.platform.coreclient.contract.PlanRequest;
import br.com.sinapse.platform.coreclient.contract.PlanResponse;
import br.com.sinapse.platform.coreclient.internal.CoreProperties;
import br.com.sinapse.platform.coreclient.internal.RestSinapseCore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * The failure modes ADR 0002 says this adapter concentrates.
 *
 * <p>Four of them, and the reason each is a test rather than a comment: they are the only ways
 * a separate process over HTTP can disappoint, they all end up as a job's failure reason, and
 * two of them differ in whether the job should be attempted again. Getting that mapping wrong
 * means either burning the retry budget on a request that cannot succeed, or giving up on a
 * core that was merely restarting.
 *
 * <p>The response checks are the other half. A partial plan reaching the database is what this
 * class exists to prevent: the sessions of a plan are written in one transaction and are
 * immutable afterwards, so a schedule with a missing topic would either abort the write or be
 * preserved forever.
 */
class RestSinapseCoreTest {

    private static final long SEED = 4242L;

    private static final UUID TOPIC = UUID.randomUUID();

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private SinapseCore core;

    @BeforeEach
    void bindClient() {
        MappingJackson2HttpMessageConverter converter =
                new MappingJackson2HttpMessageConverter(objectMapper);
        builder = RestClient.builder().baseUrl("http://core.invalid")
                .messageConverters(converters -> converters.add(0, converter));
        server = MockRestServiceServer.bindTo(builder).build();
        core = new RestSinapseCore(builder.build(), new CoreProperties("http://core.invalid",
                "/plans", Duration.ofSeconds(1), Duration.ofSeconds(1)));
    }

    @Test
    void aPlanComesBackWhenTheCoreAnswers() throws Exception {
        server.expect(requestTo("http://core.invalid/plans"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.randomSeed").value(SEED))
                .andExpect(jsonPath("$.contractVersion").value(PlanRequest.VERSION))
                .andRespond(withSuccess(json(response(session(0))), MediaType.APPLICATION_JSON));

        PlanResponse response = core.generate(request());

        assertThat(response.sessions()).hasSize(1);
        assertThat(response.metadata().coreVersion()).isEqualTo("core-test-1.0");
        server.verify();
    }

    @Test
    void aCoreThatCannotBeReachedIsUnavailable() {
        server.expect(requestTo("http://core.invalid/plans"))
                .andRespond(withException(new IOException("connection refused")));

        assertThatThrownBy(() -> core.generate(request()))
                .isInstanceOf(CoreUnavailableException.class);
    }

    @Test
    void aCoreThatDoesNotAnswerInTimeIsUnavailable() {
        server.expect(requestTo("http://core.invalid/plans"))
                .andRespond(withException(new SocketTimeoutException("read timed out")));

        assertThatThrownBy(() -> core.generate(request()))
                .as("a timeout, a refused connection and an unknown host are one failure from "
                        + "here: nothing was produced, and later is worth trying")
                .isInstanceOf(CoreUnavailableException.class);
    }

    @Test
    void aServerErrorFromTheCoreIsUnavailable() {
        server.expect(requestTo("http://core.invalid/plans"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> core.generate(request()))
                .isInstanceOf(CoreUnavailableException.class);
    }

    @Test
    void aRejectionByTheCoreIsAProtocolFailureAndNotWorthRetrying() {
        server.expect(requestTo("http://core.invalid/plans"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> core.generate(request()))
                .as("the payload would be identical on the next attempt and so would the "
                        + "answer; treating it as retryable would spend the budget establishing "
                        + "that")
                .isInstanceOf(CoreProtocolException.class);
    }

    @Test
    void anAnswerThatIsNotTheContractIsAProtocolFailure() {
        server.expect(requestTo("http://core.invalid/plans"))
                .andRespond(withSuccess("this is not json", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> core.generate(request()))
                .isInstanceOf(CoreProtocolException.class);
    }

    @Test
    void anAnswerAgainstAnotherContractVersionIsRefused() throws Exception {
        PlanResponse wrongVersion = new PlanResponse("0.9", List.of(session(0)),
                Map.of("coverage", 1.0), metadata(SEED));
        server.expect(requestTo("http://core.invalid/plans"))
                .andRespond(withSuccess(json(wrongVersion), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> core.generate(request()))
                .as("a change to this contract is a breaking change between two repositories, "
                        + "which is only true if a mismatch is refused rather than guessed at")
                .isInstanceOf(CoreProtocolException.class)
                .hasMessageContaining("0.9");
    }

    @Test
    void anEmptyScheduleIsRefused() throws Exception {
        PlanResponse empty = new PlanResponse(PlanRequest.VERSION, List.of(),
                Map.of(), metadata(SEED));
        server.expect(requestTo("http://core.invalid/plans"))
                .andRespond(withSuccess(json(empty), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> core.generate(request()))
                .as("generation is refused before it starts unless there is something to plan, "
                        + "so an empty schedule is the core failing to plan it — and storing it "
                        + "would present that failure as a plan")
                .isInstanceOf(CoreProtocolException.class);
    }

    @Test
    void anAnswerWithoutTheCoreVersionIsRefused() throws Exception {
        PlanResponse noVersion = new PlanResponse(PlanRequest.VERSION, List.of(session(0)),
                Map.of(), new PlanResponse.ExecutionMetadata("  ", SEED, 10, 5));
        server.expect(requestTo("http://core.invalid/plans"))
                .andRespond(withSuccess(json(noVersion), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> core.generate(request()))
                .as("the core's version is the fourth of the four things that make a plan "
                        + "reproducible, and the only one that cannot be known before the call")
                .isInstanceOf(CoreProtocolException.class);
    }

    @Test
    void anAnswerFromAnotherSeedIsRefused() throws Exception {
        PlanResponse otherSeed = new PlanResponse(PlanRequest.VERSION, List.of(session(0)),
                Map.of(), metadata(SEED + 1));
        server.expect(requestTo("http://core.invalid/plans"))
                .andRespond(withSuccess(json(otherSeed), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> core.generate(request()))
                .as("a run with a different seed is not the run this job recorded, and the "
                        + "record would say it was")
                .isInstanceOf(CoreProtocolException.class);
    }

    @Test
    void anIncompleteSessionIsRefused() throws Exception {
        PlanResponse missingTopic = new PlanResponse(PlanRequest.VERSION,
                List.of(new PlanResponse.ScheduledSession(null,
                        br.com.sinapse.platform.coreclient.contract.SessionKind.STUDY,
                        Instant.parse("2026-09-07T19:00:00Z"), 50, 0)),
                Map.of(), metadata(SEED));
        server.expect(requestTo("http://core.invalid/plans"))
                .andRespond(withSuccess(json(missingTopic), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> core.generate(request()))
                .isInstanceOf(CoreProtocolException.class);
    }

    @Test
    void aSessionOfNoLengthIsRefused() throws Exception {
        PlanResponse zeroLength = new PlanResponse(PlanRequest.VERSION,
                List.of(new PlanResponse.ScheduledSession(TOPIC,
                        br.com.sinapse.platform.coreclient.contract.SessionKind.STUDY,
                        Instant.parse("2026-09-07T19:00:00Z"), 0, 0)),
                Map.of(), metadata(SEED));
        server.expect(requestTo("http://core.invalid/plans"))
                .andRespond(withSuccess(json(zeroLength), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> core.generate(request()))
                .isInstanceOf(CoreProtocolException.class);
    }

    @Test
    void aRepeatedSequenceIndexIsRefused() throws Exception {
        PlanResponse repeated = new PlanResponse(PlanRequest.VERSION,
                List.of(session(0), session(0)), Map.of(), metadata(SEED));
        server.expect(requestTo("http://core.invalid/plans"))
                .andRespond(withSuccess(json(repeated), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> core.generate(request()))
                .as("the sequence is unique within a plan in the database; catching it here "
                        + "keeps a constraint violation out of the middle of the write")
                .isInstanceOf(CoreProtocolException.class);
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private static PlanRequest request() {
        return new PlanRequest(PlanRequest.VERSION,
                new PlanRequest.Horizon(LocalDate.parse("2026-09-07"),
                        LocalDate.parse("2026-10-04")),
                List.of(new PlanRequest.AvailabilitySlot(Instant.parse("2026-09-08T22:00:00Z"),
                        Instant.parse("2026-09-09T00:00:00Z"))),
                List.of(new PlanRequest.Goal(UUID.randomUUID(), null, 3)),
                List.of(new PlanRequest.Topic(TOPIC, UUID.randomUUID(), 1, "STANDARD", 50)),
                List.of(),
                List.of(),
                Map.of("generations", 3),
                SEED);
    }

    private static PlanResponse response(PlanResponse.ScheduledSession... sessions) {
        return new PlanResponse(PlanRequest.VERSION, List.of(sessions),
                Map.of("coverage", 1.0), metadata(SEED));
    }

    private static PlanResponse.ScheduledSession session(int sequenceIndex) {
        return new PlanResponse.ScheduledSession(TOPIC,
                br.com.sinapse.platform.coreclient.contract.SessionKind.STUDY,
                Instant.parse("2026-09-08T22:00:00Z"), 50, sequenceIndex);
    }

    private static PlanResponse.ExecutionMetadata metadata(long seed) {
        return new PlanResponse.ExecutionMetadata("core-test-1.0", seed, 10, 5);
    }
}
