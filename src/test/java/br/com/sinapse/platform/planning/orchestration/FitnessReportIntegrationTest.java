package br.com.sinapse.platform.planning.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.sinapse.platform.coreclient.api.CoreProtocolException;
import br.com.sinapse.platform.identity.internal.domain.Account;
import br.com.sinapse.platform.planning.api.GenerationRequestStatus;
import br.com.sinapse.platform.planning.api.GenerationRequestView;
import br.com.sinapse.platform.planning.orchestration.support.OrchestrationIntegrationTest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

/**
 * What the core reports about a run, from its answer to the screen.
 *
 * <p>The fitness report is the core's: it declares the terms, names them, weighs them, and says
 * which engine and which importance strategy ran. This side stores the report whole and passes
 * it through, and knows none of it by name. These tests hold that to account with reports of a
 * shape nothing in {@code src/main} has ever seen — {@code src/test/resources/fitness/} — so a
 * component that silently dropped, flattened, renamed or defaulted anything would be caught.
 *
 * <p>Equality is checked on the JSON tree and not on a few paths, because a path-by-path check
 * only proves the keys somebody thought to look at survived.
 *
 * <p>The cost of the run is here as well. Generations and elapsed time are the cost side of the
 * comparison between the genetic algorithm and the greedy scheduler, and a cost not written
 * down when the run finishes cannot be recovered later without running everything again.
 */
class FitnessReportIntegrationTest extends OrchestrationIntegrationTest {

    private static final String STUDY_PLANS = "/api/v1/study-plans";

    private static final String RUN_A = "fitness/fitness-report-run-a.json";

    private static final String RUN_B = "fitness/fitness-report-run-b.json";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void aReportWithUnknownTermsIsStoredAndExposedWithoutLoss() throws Exception {
        JsonNode report = fixture(RUN_A);
        core.reportFitness(asMap(report));
        Account student = studentReadyToPlan(3);
        String token = tokenFor(student);

        GenerationRequestView job = generate(student);
        assertThat(job.status()).isEqualTo(GenerationRequestStatus.READY);

        assertThat(storedFitnessOf(job.planId()))
                .as("the column holds the report as the core sent it: every key, every nested "
                        + "structure, every null")
                .isEqualTo(report);
        assertThat(fitnessAt(STUDY_PLANS + "/current", token))
                .as("the plan in force exposes the report untouched")
                .isEqualTo(report);
        assertThat(fitnessAt(summaryOf(job.planId()), token))
                .as("the plan summary exposes the same report untouched")
                .isEqualTo(report);
    }

    /**
     * Two runs, two different sets of terms.
     *
     * <p>A term the second run did not report must not appear in it as zero — a zero is a
     * statement the core did not make — and the first plan, now superseded, must still read
     * back exactly as it was reported, whatever the core has started or stopped declaring since.
     */
    @Test
    void aRunReportingAnotherSetOfTermsDisturbsNeitherPlan() throws Exception {
        JsonNode first = fixture(RUN_A);
        JsonNode second = fixture(RUN_B);
        Account student = studentReadyToPlan(3);
        String token = tokenFor(student);

        core.reportFitness(asMap(first));
        GenerationRequestView firstJob = generate(student);
        core.reportFitness(asMap(second));
        GenerationRequestView secondJob = generate(student);

        assertThat(secondJob.status()).isEqualTo(GenerationRequestStatus.READY);
        JsonNode current = fitnessAt(STUDY_PLANS + "/current", token);
        assertThat(current).isEqualTo(second);
        assertThat(current.toString())
                .as("a term the core stopped reporting is absent, not zeroed or carried over")
                .doesNotContain("load-balance")
                .doesNotContain("keyNoVersionOfThePlatformHasSeen");
        assertThat(fitnessAt(summaryOf(secondJob.planId()), token)).isEqualTo(second);

        assertThat(fitnessAt(summaryOf(firstJob.planId()), token))
                .as("the superseded plan keeps the report it was produced with")
                .isEqualTo(first);
        assertThat(storedFitnessOf(firstJob.planId())).isEqualTo(first);
    }

    @Test
    void theCostOfTheRunIsRecordedOnTheJob() {
        core.reportElapsedMillis(41_537L);
        Account student = studentReadyToPlan(3);

        GenerationRequestView job = generate(student);

        assertThat(requestColumn(job.id(), "generations", Integer.class))
                .as("the generations the core reported, which the stub takes from the "
                        + "parameters it was sent")
                .isEqualTo(3);
        assertThat(requestColumn(job.id(), "elapsed_millis", Long.class)).isEqualTo(41_537L);
        assertThat(requests.findById(job.id()).orElseThrow())
                .satisfies(stored -> {
                    assertThat(stored.generations()).isEqualTo(3);
                    assertThat(stored.elapsedMillis()).isEqualTo(41_537L);
                });
    }

    @Test
    void aJobThatFailedRecordsNoCost() {
        core.alwaysFail(() -> new CoreProtocolException("refused"));
        Account student = studentReadyToPlan(3);

        GenerationRequestView job = generate(student);

        assertThat(job.status()).isEqualTo(GenerationRequestStatus.FAILED);
        assertThat(requestColumn(job.id(), "generations", Integer.class))
                .as("there was no answer to take a cost from, and none is invented")
                .isNull();
        assertThat(requestColumn(job.id(), "elapsed_millis", Long.class)).isNull();
    }

    private JsonNode storedFitnessOf(UUID planId) throws IOException {
        String stored = jdbc.queryForObject("select fitness::text from study_plan where id = ?",
                String.class, planId);
        return objectMapper.readTree(stored);
    }

    private JsonNode fitnessAt(String path, String token) throws Exception {
        String body = mockMvc.perform(get(path)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode fitness = objectMapper.readTree(body).get("fitness");
        assertThat(fitness).as("the response carries a fitness report at %s", path).isNotNull();
        return fitness;
    }

    private static String summaryOf(UUID planId) {
        return STUDY_PLANS + "/" + planId + "/summary";
    }

    private JsonNode fixture(String path) throws IOException {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return objectMapper.readTree(in);
        }
    }

    private Map<String, Object> asMap(JsonNode report) {
        return objectMapper.convertValue(report, new TypeReference<>() {
        });
    }
}
