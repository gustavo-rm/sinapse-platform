package br.com.sinapse.platform.planning.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.sinapse.platform.coreclient.internal.CoreClientConfiguration;
import br.com.sinapse.platform.coreclient.internal.CoreProperties;
import br.com.sinapse.platform.coreclient.internal.RestSinapseCore;
import br.com.sinapse.platform.curriculum.api.CurriculumCatalog;
import br.com.sinapse.platform.curriculum.api.EffortTiers;
import br.com.sinapse.platform.curriculum.api.PrerequisiteGraph;
import br.com.sinapse.platform.identity.api.AccountDirectory;
import br.com.sinapse.platform.identity.internal.domain.Account;
import br.com.sinapse.platform.learningrecord.api.StudyHistory;
import br.com.sinapse.platform.planning.api.PlanningDirectory;
import br.com.sinapse.platform.planning.internal.config.PlanningProperties;
import br.com.sinapse.platform.planning.internal.domain.PlanGenerationRequest;
import br.com.sinapse.platform.planning.internal.service.GenerationRequestService.ClaimedJob;
import br.com.sinapse.platform.planning.orchestration.support.EngineAwareCore;
import br.com.sinapse.platform.planning.orchestration.support.OrchestrationIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;

/**
 * How the configured engine reaches the core over the wire, and what comes back of it.
 *
 * <p>The adapter is the production one — {@link RestSinapseCore}, with the client
 * {@link CoreClientConfiguration} builds from the application's own builder and mapper — pointed
 * at an {@link EngineAwareCore}, which selects engines as the real core does. The snapshot is
 * assembled by the production {@link SnapshotAssembler} from a parameter map, and the job is run
 * by the production orchestrator and written by the production writer. Only the map differs from
 * one test to the next, which is the only thing a deployment changes. Binding that map from a
 * file, {@code SPRING_APPLICATION_JSON} or an environment variable is
 * {@code AlgorithmParamsConfigurationTest}'s; here the parameters are built directly so that the
 * three deployments share the application context of the other orchestration tests instead of
 * opening three more pools on the database.
 *
 * <p>The engine asked for is recorded with the map in {@code algorithm_params}; the engine the
 * core says ran comes back under {@code fitness.engine} and is stored with the rest of the
 * fitness report on the plan. No column was added for either.
 */
class EngineSelectionIntegrationTest extends OrchestrationIntegrationTest {

    private static final EngineAwareCore CORE = EngineAwareCore.start();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PlanningProperties properties;

    @Autowired
    private PlanningDirectory planningDirectory;

    @Autowired
    private CurriculumCatalog catalog;

    @Autowired
    private PrerequisiteGraph graph;

    @Autowired
    private EffortTiers effortTiers;

    @Autowired
    private StudyHistory history;

    @Autowired
    private AccountDirectory accounts;

    @Autowired
    private GeneratedPlanWriter writer;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RestClient.Builder restClientBuilder;

    @AfterAll
    static void stopCore() {
        CORE.stop();
    }

    @Test
    void aNamedEngineIsSentAndTheEngineThatRanIsStored() {
        Run run = runWith(withEngine("ga-timeline"));

        assertThat(requestColumn(run.jobId(), "status", String.class)).isEqualTo("READY");
        assertThat(CORE.lastReceived().get("algorithmParams").get("engine").asText())
                .as("the configured name travels as algorithmParams.engine")
                .isEqualTo("ga-timeline");
        PlanGenerationRequest job = requests.findById(run.jobId()).orElseThrow();
        assertThat(job.algorithmParams())
                .as("the engine asked for is recorded with the map it was sent in")
                .containsEntry("engine", "ga-timeline");

        UUID planId = planOf(run.jobId());
        assertThat(planColumn(planId, "fitness ->> 'engine'", String.class))
                .as("the engine the core says ran reaches study_plan.fitness")
                .isEqualTo("ga-timeline");
        assertThat(planColumn(planId, "fitness ->> 'build'", String.class))
                .as("and so does the build of the core that ran it")
                .isEqualTo(EngineAwareCore.BUILD);
        assertThat(requestColumn(run.jobId(), "core_call_millis", Long.class))
                .as("the latency of the call, measured by the platform")
                .isNotNull()
                .isNotNegative();
        assertThat(requestColumn(run.jobId(), "elapsed_millis", Long.class))
                .as("still what the core reported: its reserved 0")
                .isZero();
    }

    @Test
    void withNoEngineConfiguredTheBodyCarriesNoEngineKeyAndTheDefaultIsWhatIsStored() {
        Map<String, Object> configured = properties.generation().algorithmParams();
        assertThat(configured).as("the default configuration names no engine")
                .doesNotContainKey("engine");

        Run run = runWith(configured);

        assertThat(requestColumn(run.jobId(), "status", String.class)).isEqualTo("READY");
        JsonNode sent = CORE.lastReceived().get("algorithmParams");
        assertThat(sent.has("engine")).as("absent: never empty, never null").isFalse();
        assertThat(objectMapper.convertValue(sent, Map.class))
                .as("the map is sent as configured, nothing added and nothing taken away")
                .isEqualTo(configured);
        assertThat(planColumn(planOf(run.jobId()), "fitness ->> 'engine'", String.class))
                .as("which engine ran is the core's to say, and it is stored as it said it")
                .isEqualTo(EngineAwareCore.DEFAULT_ENGINE);
    }

    /**
     * A name the core does not have: the platform cannot know, so it starts up fine, and the
     * core refuses the name with {@code 422 unknown-engine} instead of running its default. The
     * student gets a rejected job — not an unavailability worth retrying, not an internal error —
     * and nothing about the core, its address or its answer.
     */
    @Test
    void anUnknownEngineFailsTheJobAsRejectedOnceAndLeaksNothing() throws Exception {
        Run run = runWith(withEngine("no-such-engine"));

        assertThat(CORE.lastReceived().get("algorithmParams").get("engine").asText())
                .isEqualTo("no-such-engine");
        String polled = mockMvc.perform(get("/api/v1/study-plans/generation-requests/"
                                + run.jobId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(run.student())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode job = objectMapper.readTree(polled);
        assertThat(job.get("status").asText()).isEqualTo("FAILED");
        assertThat(job.get("failureReason").asText()).isEqualTo("CORE_REJECTED");
        assertThat(job.get("attemptCount").asInt()).isEqualTo(1);
        assertThat(job.has("planId")).isFalse();
        assertThat(requestService.claimNext())
                .as("a rejection is not put back in the queue: the name would be refused again")
                .isEmpty();
        // The student learns the kind of failure and nothing about the core: not its status (422),
        // address, problem type, title or the refused name. Checked member by member, because the
        // body's UUID and microsecond timestamps contain "422" by chance (K29).
        JobBodyLeakCheck.assertTellsNothingAboutTheCore(job, 422, "127.0.0.1", "http",
                "unknown-engine", "unknown engine", "no-such-engine", "exception");
        assertThat(requestColumn(run.jobId(), "core_call_millis", Long.class))
                .as("a call that produced no plan records no latency")
                .isNull();
    }

    /** The configured map with an engine added, as a deployment that names one has it. */
    private Map<String, Object> withEngine(String engine) {
        Map<String, Object> params = new HashMap<>(properties.generation().algorithmParams());
        params.put("engine", engine);
        return params;
    }

    /** One job of a fresh student, run once against the engine-aware core with these parameters. */
    private Run runWith(Map<String, Object> algorithmParams) {
        Account student = studentReadyToPlan(2);
        UUID jobId = requestService.queue(student.id(), null).id();
        ClaimedJob job = requestService.claimNext().get(0);
        assertThat(job.id()).isEqualTo(jobId);

        PlanningProperties.Generation configured = properties.generation();
        PlanningProperties deployment = new PlanningProperties(properties.maxScheduleWindow(),
                properties.maxPlanHistory(), new PlanningProperties.Generation(
                        configured.horizon(), configured.maxAttempts(), configured.retryBackoff(),
                        configured.orphanMargin(), configured.batchSize(), configured.pollCron(),
                        configured.historyWindow(), algorithmParams,
                        configured.minCalibrationSessions(), configured.minEffortFactor(),
                        configured.maxEffortFactor()));
        SnapshotAssembler assembler = new SnapshotAssembler(planningDirectory, catalog, graph,
                effortTiers, history, accounts, deployment, clock);
        CoreProperties coreProperties = new CoreProperties(CORE.baseUrl(), "/plans",
                Duration.ofSeconds(5), Duration.ofSeconds(30));
        RestSinapseCore adapter = new RestSinapseCore(new CoreClientConfiguration()
                .sinapseCoreRestClient(restClientBuilder, coreProperties), coreProperties);

        new PlanGenerationOrchestrator(assembler, adapter, requestService, writer, seeds,
                objectMapper, clock).run(job);
        return new Run(jobId, student);
    }

    private UUID planOf(UUID jobId) {
        return jdbc.queryForObject("select id from study_plan where generation_request_id = ?",
                UUID.class, jobId);
    }

    private record Run(UUID jobId, Account student) {
    }
}
