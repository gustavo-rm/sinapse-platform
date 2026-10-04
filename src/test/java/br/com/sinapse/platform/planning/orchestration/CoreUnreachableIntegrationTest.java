package br.com.sinapse.platform.planning.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.sinapse.platform.coreclient.api.SinapseCore;
import br.com.sinapse.platform.coreclient.internal.RestSinapseCore;
import br.com.sinapse.platform.curriculum.api.SubjectView;
import br.com.sinapse.platform.identity.internal.domain.Account;
import br.com.sinapse.platform.planning.support.PlanningIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A core that is not there, through the real adapter.
 *
 * <p>The other generation tests fail the stub on purpose; this one points the real
 * {@link RestSinapseCore} at a port nobody listens on, so the connection is actually refused and
 * the exception that comes back is the one production would see. Runs in the default build: it
 * needs no core, which is the point.
 *
 * <p>What it holds the platform to: the job is retried — an unreachable core is the one failure
 * worth another attempt — and then ends {@code FAILED} with {@code CORE_UNAVAILABLE}, and what the
 * student polls carries the kind of failure and nothing else. No address, no port, no exception,
 * no stack frame.
 */
class CoreUnreachableIntegrationTest extends PlanningIntegrationTest {

    private static final String GENERATION_REQUESTS = "/api/v1/study-plans/generation-requests";

    /** A port that was free a moment ago and has nobody listening on it. */
    private static final int CLOSED_PORT = closedPort();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PlanGenerationWorker worker;

    @Autowired
    private SinapseCore core;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${sinapse.planning.generation.max-attempts}")
    private int maxAttempts;

    @DynamicPropertySource
    static void unreachableCore(DynamicPropertyRegistry registry) {
        registry.add("sinapse.core.base-url", () -> "http://127.0.0.1:" + CLOSED_PORT);
    }

    @Test
    void anUnreachableCoreFailsTheJobAsUnavailableWithoutTellingTheStudentWhere() throws Exception {
        assertThat(core).as("the real adapter, not a stand-in").isInstanceOf(RestSinapseCore.class);
        Account student = readyToPlan();
        String token = tokenFor(student);

        String created = mockMvc.perform(post(GENERATION_REQUESTS)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID jobId = UUID.fromString(objectMapper.readTree(created).get("id").asText());

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            assertThat(worker.runOnce()).as("attempt %d is claimed", attempt + 1).isEqualTo(1);
        }
        assertThat(worker.runOnce()).as("no attempt beyond the configured maximum").isZero();

        String polled = mockMvc.perform(get(GENERATION_REQUESTS + "/" + jobId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode job = objectMapper.readTree(polled);

        assertThat(job.get("status").asText()).isEqualTo("FAILED");
        assertThat(job.get("failureReason").asText()).isEqualTo("CORE_UNAVAILABLE");
        assertThat(job.get("attemptCount").asInt()).isEqualTo(maxAttempts);
        assertThat(job.has("planId")).as("no plan came of it").isFalse();
        assertThat(polled)
                .as("the student learns the kind of failure and nothing about the core")
                .doesNotContain("127.0.0.1")
                .doesNotContain(String.valueOf(CLOSED_PORT))
                .doesNotContainIgnoringCase("http")
                .doesNotContainIgnoringCase("exception")
                .doesNotContainIgnoringCase("refused")
                .doesNotContain("\tat ")
                .doesNotContain("br.com.sinapse");
        assertThat(planCount(student.id())).isZero();
    }

    private Account readyToPlan() {
        Account student = student();
        LocalDate from = LocalDate.now(clock);
        for (DayOfWeek day : DayOfWeek.values()) {
            availability.declare(student.id(), day, LocalTime.of(19, 0), LocalTime.of(21, 0),
                    from, null);
        }
        SubjectView subject = subject();
        topicOf(subject);
        topicOf(subject);
        goals.set(student.id(), subject.id(), null, 3);
        return student;
    }

    private static int closedPort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
