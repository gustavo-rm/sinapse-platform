package br.com.sinapse.platform.planning.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.sinapse.platform.coreclient.api.SinapseCore;
import br.com.sinapse.platform.coreclient.internal.RestSinapseCore;
import br.com.sinapse.platform.curriculum.api.SubjectView;
import br.com.sinapse.platform.curriculum.api.TopicView;
import br.com.sinapse.platform.identity.internal.domain.Account;
import br.com.sinapse.platform.planning.api.StudyPlanView;
import br.com.sinapse.platform.planning.support.PlanningIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A core that answers, through the real adapter, with a plan that breaks an invariant.
 *
 * <p>The answer is case V3 of the audit: one session that starts ten minutes before the end of
 * the first window the request offered and runs for thirty, so it ends twenty minutes after the
 * window. It echoes the seed and names a topic that was sent, so the window is the only thing
 * wrong with it.
 *
 * <p>What the platform owes the student: the job fails as a rejection, not as an unavailability
 * worth retrying; no plan comes of it; and the plan the student already had stays in force. A
 * refused regeneration must not take away the plan that exists.
 */
class CoreAnswerBreakingAnInvariantIntegrationTest extends PlanningIntegrationTest {

    private static final String GENERATION_REQUESTS = "/api/v1/study-plans/generation-requests";

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final HttpServer CORE = sessionEndingAfterItsWindow();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PlanGenerationWorker worker;

    @Autowired
    private SinapseCore core;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void misbehavingCore(DynamicPropertyRegistry registry) {
        registry.add("sinapse.core.base-url",
                () -> "http://127.0.0.1:" + CORE.getAddress().getPort());
    }

    @AfterAll
    static void stopCore() {
        CORE.stop(0);
    }

    @Test
    void theJobIsRejectedNoPlanIsStoredAndThePlanInForceStaysActive() throws Exception {
        assertThat(core).as("the real adapter, not a stand-in").isInstanceOf(RestSinapseCore.class);
        Account student = student();
        TopicView topic = readyToPlan(student);
        StudyPlanView inForce = plan(student, topic.id(), 2);
        assertThat(planColumn(inForce.id(), "status", String.class)).isEqualTo("ACTIVE");
        String token = tokenFor(student);

        String created = mockMvc.perform(post(GENERATION_REQUESTS)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID jobId = UUID.fromString(objectMapper.readTree(created).get("id").asText());

        assertThat(worker.runOnce()).isEqualTo(1);
        assertThat(worker.runOnce())
                .as("a rejection is not retried: the same request would get the same answer")
                .isZero();

        String polled = mockMvc.perform(get(GENERATION_REQUESTS + "/" + jobId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode job = objectMapper.readTree(polled);

        assertThat(job.get("status").asText()).isEqualTo("FAILED");
        assertThat(job.get("failureReason").asText()).isEqualTo("CORE_REJECTED");
        assertThat(job.get("attemptCount").asInt()).isEqualTo(1);
        assertThat(job.has("planId")).isFalse();
        assertThat(polled)
                .as("the student learns the kind of failure and nothing about the core or its answer")
                .doesNotContain("127.0.0.1")
                .doesNotContainIgnoringCase("http")
                .doesNotContainIgnoringCase("exception")
                .doesNotContain("invariant")
                .doesNotContain("window")
                .doesNotContain(topic.id().toString())
                .doesNotContain("br.com.sinapse");

        assertThat(planCount(student.id())).as("no new plan").isEqualTo(1);
        assertThat(planColumn(inForce.id(), "status", String.class))
                .as("the plan the student already had is still the one in force")
                .isEqualTo("ACTIVE");
        assertThat(planColumn(inForce.id(), "superseded_at", java.sql.Timestamp.class)).isNull();
    }

    private TopicView readyToPlan(Account student) {
        LocalDate from = LocalDate.now(clock);
        for (DayOfWeek day : DayOfWeek.values()) {
            availability.declare(student.id(), day, LocalTime.of(19, 0), LocalTime.of(21, 0),
                    from, null);
        }
        SubjectView subject = subject();
        TopicView topic = topicOf(subject);
        goals.set(student.id(), subject.id(), null, 3);
        return topic;
    }

    /**
     * A core that reads the request and answers with one session ending twenty minutes after the
     * first window, with the seed echoed and a topic that was sent.
     */
    private static HttpServer sessionEndingAfterItsWindow() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/plans", exchange -> {
                JsonNode request = JSON.readTree(exchange.getRequestBody());
                Instant windowEnd = Instant.parse(
                        request.get("availability").get(0).get("end").asText());
                String answer = """
                        {"contractVersion":"1.0",
                         "sessions":[{"topicId":"%s","kind":"STUDY","scheduledStart":"%s",
                                      "durationMinutes":30,"sequenceIndex":0}],
                         "fitness":{},
                         "metadata":{"coreVersion":"stub-v3","randomSeed":%d,
                                     "generations":1,"elapsedMillis":1}}
                        """.formatted(request.get("topics").get(0).get("id").asText(),
                        windowEnd.minusSeconds(600), request.get("randomSeed").asLong());
                byte[] body = answer.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
