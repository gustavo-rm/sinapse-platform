package br.com.sinapse.platform.planning.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

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
import br.com.sinapse.platform.planning.support.DefaultConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.client.RestClient;

/**
 * The core's search parameters are not sent by default, and are recorded as sent when a
 * deployment sends them anyway.
 *
 * <p>{@code generations}, {@code population-size} and {@code mutation-rate} belong to the core
 * (its decision D4), which ignores them in a request. The default configuration therefore has
 * none of them, and {@code plan_generation_request.algorithm_params} keeps meaning what it has
 * always meant: what this side sent, not what the core applied. A deployment that puts them back
 * is not refused: they travel and are recorded as sent, and the core logs that it ignored them.
 *
 * <p>The map is the one the default configuration really binds to ({@link DefaultConfiguration}),
 * put in place of the test profile's, which carries parameters of its own. The rest of the path
 * is production: the assembler, the HTTP adapter, the orchestrator and the writer, against an
 * {@link EngineAwareCore} that keeps the body it received.
 */
class DefaultAlgorithmParamsIntegrationTest extends OrchestrationIntegrationTest {

    private static final String[] CORE_OWNED = {"generations", "population-size", "mutation-rate"};

    private static final EngineAwareCore CORE = EngineAwareCore.start();

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
    void theDefaultConfigurationSendsNoneOfTheCoresSearchParametersAndRecordsNone() {
        Map<String, Object> configured = DefaultConfiguration.algorithmParams();
        assertThat(configured).doesNotContainKeys(CORE_OWNED);

        Run run = runWith(configured);

        assertThat(requestColumn(run.jobId(), "status", String.class)).isEqualTo("READY");
        JsonNode sent = CORE.lastReceived().get("algorithmParams");
        assertThat(sent).as("the member is sent, as an object").isNotNull();
        assertThat(sent.isObject()).isTrue();
        for (String key : CORE_OWNED) {
            assertThat(sent.has(key)).as("%s is not in the body sent to the core", key).isFalse();
        }
        assertThat(objectMapper.convertValue(sent, Map.class))
                .as("the body carries the default map as bound, nothing added")
                .isEqualTo(configured);

        PlanGenerationRequest job = requests.findById(run.jobId()).orElseThrow();
        assertThat(job.algorithmParams()).as("recorded as sent")
                .isEqualTo(configured)
                .doesNotContainKeys(CORE_OWNED);
        assertThat(jdbc.queryForObject("select count(*) from plan_generation_request "
                        + "where account_id = ? and algorithm_params is not null "
                        + "and jsonb_exists_any(algorithm_params, ?::text[])",
                Integer.class, run.student().id(), "{" + String.join(",", CORE_OWNED) + "}"))
                .as("no row written for this student carries any of them")
                .isZero();
        assertThat(jdbc.queryForObject("select count(*) from plan_generation_request "
                        + "where account_id = ? and algorithm_params is not null",
                Integer.class, run.student().id()))
                .as("and the row that was checked exists")
                .isEqualTo(1);
        assertThat(planColumn(planOf(run.jobId()), "fitness ->> 'engine'", String.class))
                .as("with nothing named, the core runs its default")
                .isEqualTo(EngineAwareCore.DEFAULT_ENGINE);
    }

    @Test
    void aDeploymentThatPutsThemBackSendsThemAndTheyAreRecordedAsSent() {
        Map<String, Object> configured = DefaultConfiguration.algorithmParams(Map.of(
                "SPRING_APPLICATION_JSON", "{\"sinapse\":{\"planning\":{\"generation\":"
                        + "{\"algorithm-params\":{\"population-size\":120,\"generations\":400,"
                        + "\"mutation-rate\":0.05}}}}}"));
        assertThat(configured).as("not refused at startup: the core decides what it ignores")
                .containsOnlyKeys(CORE_OWNED);

        Run run = runWith(configured);

        assertThat(requestColumn(run.jobId(), "status", String.class)).isEqualTo("READY");
        JsonNode sent = CORE.lastReceived().get("algorithmParams");
        assertThat(sent.get("population-size").asInt()).isEqualTo(120);
        assertThat(sent.get("generations").asInt()).isEqualTo(400);
        assertThat(sent.get("mutation-rate").asDouble()).isEqualTo(0.05);
        assertThat(objectMapper.convertValue(sent, Map.class)).isEqualTo(configured);

        assertThat(requests.findById(run.jobId()).orElseThrow().algorithmParams())
                .as("recorded as sent, which is not as applied")
                .isEqualTo(configured);
        assertThat(requestColumn(run.jobId(), "algorithm_params ->> 'generations'", String.class))
                .isEqualTo("400");
        assertThat(requestColumn(run.jobId(), "algorithm_params ->> 'mutation-rate'",
                String.class))
                .isEqualTo("0.05");
    }

    /** One job of a fresh student, run once against the engine-aware core with these parameters. */
    private Run runWith(Map<String, Object> algorithmParams) {
        Account student = studentReadyToPlan(2);
        UUID jobId = requestService.queue(student.id(), null).id();
        ClaimedJob job = requestService.claimNext().get(0);
        assertThat(job.id()).isEqualTo(jobId);

        SnapshotAssembler assembler = new SnapshotAssembler(planningDirectory, catalog, graph,
                effortTiers, history, accounts,
                DefaultConfiguration.withAlgorithmParams(properties, algorithmParams), clock);
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
