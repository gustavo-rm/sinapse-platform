package br.com.sinapse.platform.planning.orchestration.endtoend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.sinapse.platform.curriculum.api.CatalogCuration;
import br.com.sinapse.platform.curriculum.api.CurriculumCatalog;
import br.com.sinapse.platform.curriculum.api.EdgeProvenance;
import br.com.sinapse.platform.curriculum.api.EdgeStrength;
import br.com.sinapse.platform.curriculum.api.EffortTiers;
import br.com.sinapse.platform.curriculum.api.PrerequisiteGraph;
import br.com.sinapse.platform.curriculum.api.SubjectView;
import br.com.sinapse.platform.curriculum.api.TopicView;
import br.com.sinapse.platform.identity.api.AccountDirectory;
import br.com.sinapse.platform.identity.internal.domain.Account;
import br.com.sinapse.platform.learningrecord.api.StudyHistory;
import br.com.sinapse.platform.planning.api.PlanningDirectory;
import br.com.sinapse.platform.planning.internal.config.PlanningProperties;
import br.com.sinapse.platform.planning.internal.domain.PlanGenerationRequest;
import br.com.sinapse.platform.planning.orchestration.PlanGenerationWorker;
import br.com.sinapse.platform.planning.orchestration.SnapshotAssembler;
import br.com.sinapse.platform.planning.support.DefaultConfiguration;
import br.com.sinapse.platform.planning.support.PlanningIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The default configuration against the real core: the algorithm parameters a deployment sends
 * when it sets nothing but, at most, the engine in its environment.
 *
 * <p>The map is what the base {@code application.yml} binds to under this JVM's own environment
 * ({@link DefaultConfiguration}), put in place of the test profile's. With nothing set it is
 * empty, and the core has to accept {@code "algorithmParams": {}} and answer with its default
 * engine. With {@value #ENGINE_VARIABLE} set — the variable an operator would set — its only
 * entry is {@code engine}, created by relaxed binding in the empty map, and the core has to run
 * that engine. In neither case does the body carry {@code generations}, {@code population-size}
 * or {@code mutation-rate}, which the core owns (its decision D4).
 *
 * <p>The scenario is the small one of the other end-to-end tests, and one synthetic account is
 * created per run. Out of the default build, like them ({@code ./mvnw verify -Pcore-e2e}).
 */
@Tag(CoreEndToEndIntegrationTest.TAG)
@Import(DefaultParametersEndToEndIntegrationTest.DefaultParameters.class)
class DefaultParametersEndToEndIntegrationTest extends PlanningIntegrationTest {

    static final String ENGINE_VARIABLE = "SINAPSE_PLANNING_GENERATION_ALGORITHMPARAMS_ENGINE";

    private static final String GENERATION_REQUESTS = "/api/v1/study-plans/generation-requests";

    private static final String[] CORE_OWNED = {"generations", "population-size", "mutation-rate"};

    /** The deployment's map: the default configuration under this JVM's environment. */
    private static final Map<String, Object> DEPLOYMENT =
            DefaultConfiguration.algorithmParams(System.getenv());

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PlanGenerationWorker worker;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void realCore(DynamicPropertyRegistry registry) {
        registry.add("sinapse.core.base-url", () -> RealCore.get().baseUrl());
    }

    @Test
    void theDefaultParametersAreAcceptedAndTheEngineThatRanIsStored() throws Exception {
        String named = System.getenv(ENGINE_VARIABLE);
        assertThat(DEPLOYMENT)
                .as("the default map, plus the engine when the environment names one")
                .isEqualTo(named == null ? Map.of() : Map.of("engine", named))
                .doesNotContainKeys(CORE_OWNED);
        String expected = named == null ? RealCore.DEFAULT_ENGINE : named;

        Account student = student();
        goalWithPrerequisiteChain(student);
        String token = tokenFor(student);
        String created = mockMvc.perform(post(GENERATION_REQUESTS)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID jobId = UUID.fromString(objectMapper.readTree(created).get("id").asText());
        worker.runOnce();

        JsonNode job = objectMapper.readTree(mockMvc.perform(get(GENERATION_REQUESTS + "/" + jobId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        String state = job.get("status").asText();
        PlanGenerationRequest stored = requests.findById(jobId).orElseThrow();
        if (!"READY".equals(state)) {
            report(jobId, state, null, 0, stored.coreCallMillis());
            fail("algorithmParams " + DEPLOYMENT + " against " + RealCore.get().origin()
                    + ": the job ended " + state + " with " + job.path("failureReason")
                    .asText("none") + ". CORE_REJECTED is a 4xx from the core or an answer "
                    + "outside the contract; the platform log names which.");
        }

        UUID planId = UUID.fromString(job.get("planId").asText());
        String effective = planColumn(planId, "fitness ->> 'engine'", String.class);
        int sessions = directory.sessionsOfPlan(planId).size();
        report(jobId, state, effective, sessions, stored.coreCallMillis());

        assertThat(stored.snapshot().get("algorithmParams"))
                .as("the document sent carries the map, an empty object when nothing is named")
                .isEqualTo(DEPLOYMENT);
        assertThat(stored.algorithmParams()).as("recorded as sent").isEqualTo(DEPLOYMENT);
        assertThat(effective).as("the engine the core says ran").isEqualTo(expected);
        assertThat(sessions).as("a READY job has a stored plan with sessions").isPositive();
        assertThat(stored.coreCallMillis()).as("core_call_millis").isNotNull().isNotNegative();
    }

    /** One line per run, for whoever reads it. */
    private void report(UUID jobId, String state, String effective, int sessions,
            Long coreCallMillis) {
        System.out.printf("DEFAULT-PARAMS algorithmParams=%s job=%s state=%s effective=%s "
                + "sessions=%d core_call_millis=%s core=%s%n", DEPLOYMENT, jobId, state,
                effective, sessions, coreCallMillis, RealCore.get().origin());
    }

    /** The scenario of the existing end-to-end tests: four topics, three hard edges. */
    private void goalWithPrerequisiteChain(Account student) {
        LocalDate from = LocalDate.now(clock);
        for (DayOfWeek day : DayOfWeek.values()) {
            availability.declare(student.id(), day, LocalTime.of(19, 0), LocalTime.of(21, 0),
                    from, null);
        }
        SubjectView subject = subject();
        List<TopicView> topics = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            topics.add(topicOf(subject));
        }
        hard(topics.get(3), topics.get(2));
        hard(topics.get(2), topics.get(1));
        hard(topics.get(3), topics.get(1));
        goals.set(student.id(), subject.id(), null, 3);
    }

    private void hard(TopicView prerequisite, TopicView dependent) {
        curation.addEdge(new CatalogCuration.EdgeDefinition(prerequisite.id(), dependent.id(),
                EdgeStrength.HARD, EdgeProvenance.CURATED, "end-to-end fixture",
                UUID.randomUUID()));
    }

    /** Assembles with the deployment's map in place of the test profile's. */
    @TestConfiguration
    static class DefaultParameters {

        @Bean
        @Primary
        SnapshotAssembler defaultParametersAssembler(PlanningDirectory planning,
                CurriculumCatalog catalog, PrerequisiteGraph graph, EffortTiers effortTiers,
                StudyHistory history, AccountDirectory accounts, PlanningProperties bound,
                Clock clock) {
            return new SnapshotAssembler(planning, catalog, graph, effortTiers, history, accounts,
                    DefaultConfiguration.withAlgorithmParams(bound, DEPLOYMENT), clock);
        }
    }
}
