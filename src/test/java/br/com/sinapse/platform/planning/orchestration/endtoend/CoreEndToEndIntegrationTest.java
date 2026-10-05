package br.com.sinapse.platform.planning.orchestration.endtoend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.sinapse.platform.coreclient.api.CoreProtocolException;
import br.com.sinapse.platform.coreclient.api.CoreUnavailableException;
import br.com.sinapse.platform.coreclient.api.SinapseCore;
import br.com.sinapse.platform.coreclient.contract.EdgeStrength;
import br.com.sinapse.platform.coreclient.contract.PlanRequest;
import br.com.sinapse.platform.coreclient.contract.PlanResponse;
import br.com.sinapse.platform.curriculum.api.CatalogCuration;
import br.com.sinapse.platform.curriculum.api.EdgeProvenance;
import br.com.sinapse.platform.curriculum.api.SubjectView;
import br.com.sinapse.platform.curriculum.api.TopicView;
import br.com.sinapse.platform.identity.internal.domain.Account;
import br.com.sinapse.platform.planning.api.GenerationRequestStatus;
import br.com.sinapse.platform.planning.api.PlannedSessionView;
import br.com.sinapse.platform.planning.internal.domain.PlanGenerationRequest;
import br.com.sinapse.platform.planning.orchestration.PlanGenerationWorker;
import br.com.sinapse.platform.planning.orchestration.support.FixedSeedSource;
import br.com.sinapse.platform.planning.support.PlanningIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Plan generation against the real optimisation core, from the student's request to the plan
 * on the screen.
 *
 * <p>Every other generation test runs against {@code StubSinapseCore}. This one replaces nothing
 * on the path: the HTTP adapter, its timeouts, the wire format, the core's own engines and its
 * own invariants are the real ones. Only the seed is pinned, because the reproducibility check
 * is the point of half of it and a random seed cannot be compared.
 *
 * <p><strong>Out of the default build.</strong> Tagged {@value #TAG}, which the build excludes
 * unless the {@code core-e2e} Maven profile is active ({@code ./mvnw verify -Pcore-e2e}). It
 * needs Docker, like every database test, and on top of that a core — see {@link RealCore} for
 * the two ways to provide one and for the single parameter that switches the engine.
 *
 * <p><strong>A failure says which side failed.</strong> A core that is not there fails with
 * {@code CORE_UNAVAILABLE}, a core that answers outside the contract with {@code CORE_REJECTED},
 * and the assertion message names both and where to look. The contract is also checked on its
 * own, first, with the reference request both repositories share, so that a divergence reads as
 * a divergence and not as a plan that did not appear.
 *
 * <p><strong>The stop rule.</strong> If a job reaches {@code READY} with a stored plan that breaks
 * one of the eight checks of {@code RestSinapseCore.validated}, the validation has a hole, and
 * the assertions say so in those words: that finding outranks the test.
 */
@Tag(CoreEndToEndIntegrationTest.TAG)
@Import(CoreEndToEndIntegrationTest.PinnedSeed.class)
class CoreEndToEndIntegrationTest extends PlanningIntegrationTest {

    /** The tag the build excludes by default. */
    static final String TAG = "core-e2e";

    private static final String GENERATION_REQUESTS = "/api/v1/study-plans/generation-requests";

    private static final String CURRENT_PLAN = "/api/v1/study-plans/current";

    private static final long SEED = 7_362_819_450_172_837_461L;

    /** Read back from the job only; the platform never names a fitness term in src/main. */
    private static final String ENGINE_REPORT_KEY = "engine";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PlanGenerationWorker worker;

    @Autowired
    private FixedSeedSource seeds;

    @Autowired
    private SinapseCore core;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void realCore(DynamicPropertyRegistry registry) {
        registry.add("sinapse.core.base-url", () -> RealCore.get().baseUrl());
        registry.add("sinapse.planning.generation.algorithm-params.engine",
                () -> RealCore.get().engine());
    }

    @BeforeEach
    void pinTheSeed() {
        seeds.setSeed(SEED);
    }

    /**
     * The contract on its own, before any plan: the reference request both repositories keep,
     * sent to the real core through the real adapter.
     */
    @Test
    void theCoreAnswersTheReferenceRequestWithinTheContract() throws IOException {
        PlanRequest reference = objectMapper.readValue(read("contract/plan-request-v1.0.json"),
                PlanRequest.class);
        Map<String, Object> params = new HashMap<>(reference.algorithmParams());
        params.put("engine", RealCore.get().engine());
        PlanRequest request = new PlanRequest(reference.contractVersion(), reference.horizon(),
                reference.availability(), reference.goals(), reference.topics(),
                reference.prerequisites(), reference.history(), params, reference.randomSeed());

        PlanResponse response;
        try {
            response = core.generate(request);
        } catch (CoreUnavailableException unavailable) {
            throw new AssertionError(diagnosis() + " could not be reached: "
                    + unavailable.getMessage() + ". Is it running, and is POST /plans served "
                    + "(profile baseline-core)?", unavailable);
        } catch (CoreProtocolException divergence) {
            throw new AssertionError(diagnosis() + " did not accept or did not answer the "
                    + "reference request of contract " + PlanRequest.VERSION + ": "
                    + divergence.getMessage() + ". The two sides no longer speak the same "
                    + "contract.", divergence);
        }

        assertThat(response.contractVersion()).isEqualTo(PlanRequest.VERSION);
        assertThat(response.metadata().randomSeed()).isEqualTo(request.randomSeed());
        assertThat(response.fitness().get(ENGINE_REPORT_KEY))
                .as("the core ran the engine it was asked for, so the condition under test is "
                        + "the one recorded")
                .isEqualTo(RealCore.get().engine());
    }

    /**
     * The whole path, twice with the same seed.
     *
     * <p>Requested over HTTP, as the student requests it; run by the same worker the schedule
     * runs; read back over HTTP from the route the plan screen calls. Then requested again with
     * the same seed and nothing else changed, and the two stored plans compared session by
     * session — the demonstration ADR 0007 requires and that until now was designed and not
     * shown.
     */
    @Test
    void aRequestedPlanComesBackFromTheRealCoreAndTheSameSeedReproducesIt() throws Exception {
        Account student = student();
        String token = tokenFor(student);
        List<TopicView> topics = goalWithPrerequisiteChain(student);

        UUID firstJob = requestAndRun(token, student);
        UUID firstPlan = assertReady(student, firstJob);
        assertTheRunWasRecorded(firstJob);
        assertTheStoredPlanHoldsEveryInvariant(firstJob, firstPlan, topics);

        String current = mockMvc.perform(get(CURRENT_PLAN)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode body = objectMapper.readTree(current);
        assertThat(body.get("id").asText()).isEqualTo(firstPlan.toString());
        assertThat(body.get("generationRequestId").asText()).isEqualTo(firstJob.toString());
        mockMvc.perform(get("/api/v1/study-plans/" + firstPlan + "/sessions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(objectMapper.readTree(
                        result.getResponse().getContentAsString()).size())
                        .as("the plan in force is served with its sessions")
                        .isEqualTo(directory.sessionsOfPlan(firstPlan).size())
                        .isPositive());

        UUID secondJob = requestAndRun(token, student);
        UUID secondPlan = assertReady(student, secondJob);
        assertTheStoredPlanHoldsEveryInvariant(secondJob, secondPlan, topics);

        PlanGenerationRequest first = requests.findById(firstJob).orElseThrow();
        PlanGenerationRequest second = requests.findById(secondJob).orElseThrow();
        assertThat(objectMapper.valueToTree(second.snapshot()).equals(
                objectMapper.valueToTree(first.snapshot())))
                .as("the second run was sent exactly what the first was; otherwise an identical "
                        + "plan would prove nothing about the seed")
                .isTrue();
        assertThat(second.randomSeed()).isEqualTo(first.randomSeed()).isEqualTo(SEED);
        assertThat(second.coreVersion()).isEqualTo(first.coreVersion());

        assertThat(shapeOf(secondPlan))
                .as("same snapshot, same parameters, same seed, same core version: same plan, "
                        + "topic, kind, start, duration and position of every session (ADR 0007)")
                .isEqualTo(shapeOf(firstPlan));
        assertThat(storedFitness(secondPlan))
                .as("and the same judgement of it")
                .isEqualTo(storedFitness(firstPlan));
    }

    private UUID requestAndRun(String token, Account student) throws Exception {
        String created = mockMvc.perform(post(GENERATION_REQUESTS)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID jobId = UUID.fromString(objectMapper.readTree(created).get("id").asText());
        worker.runOnce();
        return jobId;
    }

    private UUID assertReady(Account student, UUID jobId) throws Exception {
        JsonNode job = objectMapper.readTree(mockMvc.perform(get(GENERATION_REQUESTS + "/" + jobId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(student)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        String state = job.get("status").asText();
        if (!GenerationRequestStatus.READY.name().equals(state)) {
            String reason = job.path("failureReason").asText("none");
            fail(switch (reason) {
                case "CORE_UNAVAILABLE" -> diagnosis() + " did not answer (CORE_UNAVAILABLE). "
                        + "Is it running, and is POST /plans served (profile baseline-core)?";
                case "CORE_REJECTED" -> diagnosis() + " answered, and the platform refused the "
                        + "answer (CORE_REJECTED): a 4xx from the core or a response outside "
                        + "contract " + PlanRequest.VERSION + ". The platform log names which.";
                default -> "The job ended " + state + " with reason " + reason + " against "
                        + diagnosis() + ".";
            });
        }
        assertThat(job.get("planId").isTextual()).as("a ready job names its plan").isTrue();
        return UUID.fromString(job.get("planId").asText());
    }

    /** The four things ADR 0007 needs, and the cost SP-3 started keeping. */
    private void assertTheRunWasRecorded(UUID jobId) {
        PlanGenerationRequest job = requests.findById(jobId).orElseThrow();
        assertThat(job.snapshot()).as("snapshot").isNotNull().containsKey("topics");
        assertThat(job.coreVersion()).as("core_version").isNotBlank();
        assertThat(job.algorithmParams()).as("algorithm_params")
                .containsEntry("engine", RealCore.get().engine());
        assertThat(job.randomSeed()).as("random_seed").isEqualTo(SEED);
        assertThat(requestColumn(jobId, "random_seed", Long.class)).isEqualTo(SEED);
        assertThat(requestColumn(jobId, "core_version", String.class))
                .isEqualTo(job.coreVersion());
        assertThat(requestColumn(jobId, "algorithm_params::text", String.class))
                .contains(RealCore.get().engine());
        // Recorded as the core reported them, which is what is asserted. Whether the values are
        // meaningful is the core's to decide: today it reports elapsedMillis as 0 by design.
        assertThat(job.generations()).as("generations, as the core reported it").isNotNull()
                .isNotNegative();
        assertThat(job.elapsedMillis()).as("elapsedMillis, as the core reported it").isNotNull()
                .isNotNegative();
        assertThat(job.attemptCount()).isEqualTo(1);
    }

    /**
     * Every invariant a stored plan can be held to, split by who promised it.
     *
     * <p>The eight of {@code RestSinapseCore.validated} first: the platform promised them, and a
     * READY job that breaks one is a hole in the validation. Then the four the core asserts and
     * the platform does not (horizon, availability, overlap, topics that were sent), and the
     * hard prerequisite order — the core's promises, checked here because this is the first test
     * that can.
     */
    private void assertTheStoredPlanHoldsEveryInvariant(UUID jobId, UUID planId,
            List<TopicView> topics) {
        PlanGenerationRequest job = requests.findById(jobId).orElseThrow();
        PlanRequest sent = objectMapper.convertValue(job.snapshot(), PlanRequest.class);
        List<PlannedSessionView> sessions = directory.sessionsOfPlan(planId);

        String hole = "STOP — RestSinapseCore.validated has a hole: job " + jobId
                + " reached READY with a stored plan that ";
        assertThat(sessions).as(hole + "has no sessions").isNotEmpty();
        assertThat(job.coreVersion()).as(hole + "names no core version").isNotBlank();
        assertThat(job.randomSeed()).as(hole + "ran with another seed")
                .isEqualTo(sent.randomSeed());
        assertThat(sessions).as(hole + "has an incomplete session").allSatisfy(session -> {
            assertThat(session.topicId()).isNotNull();
            assertThat(session.kind()).isNotNull();
            assertThat(session.scheduledStart()).isNotNull();
        });
        assertThat(sessions).as(hole + "has a session of no length")
                .allSatisfy(session -> assertThat(session.durationMinutes()).isPositive());
        assertThat(sessions.stream().map(PlannedSessionView::sequenceIndex).distinct().count())
                .as(hole + "repeats a sequence index").isEqualTo(sessions.size());

        Set<UUID> sentTopics = sent.topics().stream().map(PlanRequest.Topic::id)
                .collect(Collectors.toSet());
        assertThat(sentTopics).as("every topic of the goal was sent")
                .containsAll(topics.stream().map(TopicView::id).toList());
        List<PlannedSessionView> ordered = sessions.stream()
                .sorted(java.util.Comparator.comparingInt(PlannedSessionView::sequenceIndex))
                .toList();
        for (int index = 0; index < ordered.size(); index++) {
            PlannedSessionView session = ordered.get(index);
            String where = "core invariant, session " + session.sequenceIndex() + ": ";
            assertThat(sentTopics).as(where + "a topic that was sent")
                    .contains(session.topicId());
            // The windows sent are the horizon's days only, so whole containment in one of them
            // is containment in the horizon as well.
            assertThat(sent.availability()).as(where + "inside one availability window")
                    .anySatisfy(slot -> {
                        assertThat(session.scheduledStart()).isAfterOrEqualTo(slot.start());
                        assertThat(session.scheduledEnd()).isBeforeOrEqualTo(slot.end());
                    });
            if (index > 0) {
                assertThat(session.scheduledStart())
                        .as(where + "after the previous session ends, in sequence order")
                        .isAfterOrEqualTo(ordered.get(index - 1).scheduledEnd());
            }
        }

        assertNoDependentBeforeItsHardPrerequisites(sent, sessions);
    }

    /**
     * Requirement (e): no session of a dependent topic before its hard prerequisites.
     *
     * <p>The core's own rule, stated in time rather than in sequence position because time is
     * what the student sees: every session of a scheduled dependent topic starts after the first
     * session of each of its {@code HARD} prerequisites has ended. A dependent scheduled with a
     * prerequisite missing from the plan breaks it too — nothing was studied before the run.
     */
    private static void assertNoDependentBeforeItsHardPrerequisites(PlanRequest sent,
            List<PlannedSessionView> sessions) {
        Set<UUID> sentTopics = sent.topics().stream().map(PlanRequest.Topic::id)
                .collect(Collectors.toSet());
        List<PlanRequest.PrerequisiteEdge> hard = sent.prerequisites().stream()
                .filter(edge -> edge.strength() == EdgeStrength.HARD)
                .filter(edge -> sentTopics.contains(edge.prerequisiteTopicId())
                        && sentTopics.contains(edge.dependentTopicId()))
                .toList();
        assertThat(hard).as("the run had hard edges to honour, or this check proves nothing")
                .hasSize(3);

        Map<UUID, List<PlannedSessionView>> byTopic = sessions.stream()
                .collect(Collectors.groupingBy(PlannedSessionView::topicId));
        assertThat(hard).as("at least one dependent topic was scheduled, or the order check "
                        + "below holds vacuously; the topics left out are in the stored fitness")
                .anyMatch(edge -> byTopic.containsKey(edge.dependentTopicId()));
        for (PlanRequest.PrerequisiteEdge edge : hard) {
            List<PlannedSessionView> dependent = byTopic.get(edge.dependentTopicId());
            if (dependent == null) {
                continue;
            }
            List<PlannedSessionView> prerequisite = byTopic.get(edge.prerequisiteTopicId());
            assertThat(prerequisite)
                    .as("topic %s is scheduled while its hard prerequisite %s is not in the plan",
                            edge.dependentTopicId(), edge.prerequisiteTopicId())
                    .isNotNull();
            Instant prerequisiteDone = prerequisite.stream()
                    .min(java.util.Comparator.comparing(PlannedSessionView::scheduledStart))
                    .orElseThrow().scheduledEnd();
            assertThat(dependent).as("no session of %s before its hard prerequisite %s",
                            edge.dependentTopicId(), edge.prerequisiteTopicId())
                    .allSatisfy(session -> assertThat(session.scheduledStart())
                            .isAfterOrEqualTo(prerequisiteDone));
        }
    }

    /**
     * A goal on a subject of four topics, three of them chained by hard prerequisites.
     *
     * <p>The chain is declared against curricular order — the last topic is the foundation — so
     * that a scheduler following position alone would break it and the check has something to
     * catch.
     */
    private List<TopicView> goalWithPrerequisiteChain(Account student) {
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
        return topics;
    }

    private void hard(TopicView prerequisite, TopicView dependent) {
        curation.addEdge(new CatalogCuration.EdgeDefinition(prerequisite.id(), dependent.id(),
                br.com.sinapse.platform.curriculum.api.EdgeStrength.HARD, EdgeProvenance.CURATED,
                "end-to-end fixture", UUID.randomUUID()));
    }

    private List<String> shapeOf(UUID planId) {
        return directory.sessionsOfPlan(planId).stream()
                .map(session -> session.sequenceIndex() + " " + session.topicId() + " "
                        + session.kind() + " " + session.scheduledStart() + " "
                        + session.durationMinutes())
                .toList();
    }

    private JsonNode storedFitness(UUID planId) throws IOException {
        return objectMapper.readTree(planColumn(planId, "fitness::text", String.class));
    }

    private <T> T requestColumn(UUID requestId, String column, Class<T> type) {
        return jdbc.queryForObject(
                "select " + column + " from plan_generation_request where id = ?", type, requestId);
    }

    private static String diagnosis() {
        RealCore real = RealCore.get();
        return "The Sinapse Core (" + real.origin() + ", engine " + real.engine() + ")";
    }

    private static byte[] read(String path) throws IOException {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return in.readAllBytes();
        }
    }

    /** Pins the seed and nothing else: the core is the real one. */
    @TestConfiguration
    static class PinnedSeed {

        @Bean
        @Primary
        FixedSeedSource pinnedSeedSource() {
            return new FixedSeedSource();
        }
    }
}
