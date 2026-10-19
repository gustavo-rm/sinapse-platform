package br.com.sinapse.platform.planning.orchestration.endtoend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.sinapse.platform.curriculum.api.CatalogCuration;
import br.com.sinapse.platform.curriculum.api.EdgeProvenance;
import br.com.sinapse.platform.curriculum.api.EdgeStrength;
import br.com.sinapse.platform.curriculum.api.SubjectView;
import br.com.sinapse.platform.curriculum.api.TopicView;
import br.com.sinapse.platform.identity.internal.domain.Account;
import br.com.sinapse.platform.planning.internal.domain.PlanGenerationRequest;
import br.com.sinapse.platform.planning.orchestration.PlanGenerationWorker;
import br.com.sinapse.platform.planning.support.PlanningIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.web.servlet.MockMvc;

/**
 * One engine of the real core, chosen the way a deployment chooses it, from the student's
 * request to the stored plan.
 *
 * <p>Each subclass is one deployment of the platform that names one engine in
 * {@code sinapse.planning.generation.algorithm-params.engine}; all of them talk to the same
 * core, the one {@link RealCore} provides. What is proven is that the choice arrives and that
 * the plan is valid: the job reaches {@code READY}, so the plan passed
 * {@code RestSinapseCore.validated}; the engine the core says ran, stored under
 * {@code study_plan.fitness.engine}, is the one asked for; and the platform timed the call.
 * Nothing here says anything about the quality of a plan, and the scenario is deliberately the
 * small one the existing end-to-end test uses, not the real catalogue.
 *
 * <p><strong>The stop rule.</strong> An effective engine other than the one asked for means the
 * core does not honour {@code algorithmParams.engine}, and the assertion says so in those words.
 *
 * <p>One synthetic account per run, created here, so the per-account limit on generation
 * requests is never what decides the outcome.
 */
@Tag(CoreEndToEndIntegrationTest.TAG)
abstract class EngineMatrixEndToEnd extends PlanningIntegrationTest {

    private static final String GENERATION_REQUESTS = "/api/v1/study-plans/generation-requests";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PlanGenerationWorker worker;

    @Autowired
    private ObjectMapper objectMapper;

    /** Points the platform at the real core and names the engine, as a deployment would. */
    static void deployment(DynamicPropertyRegistry registry, String engine) {
        registry.add("sinapse.core.base-url", () -> RealCore.get().baseUrl());
        registry.add("sinapse.planning.generation.algorithm-params.engine", () -> engine);
    }

    /** The engine this deployment names. */
    abstract String engine();

    @Test
    void theNamedEngineRunsAndItsPlanIsStoredAsValid() throws Exception {
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
        String reason = job.path("failureReason").asText("none");
        PlanGenerationRequest stored = requests.findById(jobId).orElseThrow();
        if (!"READY".equals(state)) {
            report(state, null, 0, stored.coreCallMillis());
            fail("engine " + engine() + " against " + RealCore.get().origin() + ": the job ended "
                    + state + " with " + reason + ". CORE_REJECTED is a 4xx from the core or an "
                    + "answer outside the contract; the platform log names which.");
        }

        UUID planId = UUID.fromString(job.get("planId").asText());
        String effective = planColumn(planId, "fitness ->> 'engine'", String.class);
        int sessions = directory.sessionsOfPlan(planId).size();
        report(state, effective, sessions, stored.coreCallMillis());

        assertThat(stored.algorithmParams()).as("the engine asked for is on the record")
                .containsEntry("engine", engine());
        assertThat(effective)
                .as("STOP — the core ran %s when asked for %s: it does not honour "
                        + "algorithmParams.engine", effective, engine())
                .isEqualTo(engine());
        assertThat(sessions).as("a READY job has a stored plan with sessions").isPositive();
        assertThat(stored.coreCallMillis()).as("core_call_millis").isNotNull().isNotNegative();
    }

    /** One line per engine, for whoever reads the run: the table the report asks for. */
    private void report(String state, String effective, int sessions, Long coreCallMillis) {
        System.out.printf("ENGINE-MATRIX engine=%s state=%s effective=%s sessions=%d "
                + "core_call_millis=%s core=%s%n", engine(), state, effective, sessions,
                coreCallMillis, RealCore.get().origin());
    }

    /** The scenario of the existing end-to-end test: four topics, three hard edges. */
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
}
