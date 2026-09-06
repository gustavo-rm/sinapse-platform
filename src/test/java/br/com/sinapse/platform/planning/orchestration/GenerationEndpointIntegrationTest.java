package br.com.sinapse.platform.planning.orchestration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.sinapse.platform.coreclient.api.CoreProtocolException;
import br.com.sinapse.platform.identity.internal.domain.Account;
import br.com.sinapse.platform.planning.api.GenerationRequestView;
import br.com.sinapse.platform.planning.orchestration.support.OrchestrationIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

/** Asking for a plan over HTTP, and polling for it. */
class GenerationEndpointIntegrationTest extends OrchestrationIntegrationTest {

    private static final String GENERATION_REQUESTS = "/api/v1/study-plans/generation-requests";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void anAnonymousCallerReachesNothing() throws Exception {
        mockMvc.perform(post(GENERATION_REQUESTS)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(GENERATION_REQUESTS + "/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    /**
     * The shape ADR 0002 chose: ask, get an identifier, poll.
     *
     * <p>The optimisation is CPU-bound and runs for seconds to minutes, so the creation call
     * cannot wait for it — which is why the job exists at all, and why what comes back first is
     * a job and not a plan.
     */
    @Test
    void askingForAPlanReturnsAJobToPollAndThenAPlan() throws Exception {
        Account student = studentReadyToPlan(3);
        String token = tokenFor(student);

        mockMvc.perform(post(GENERATION_REQUESTS)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.attemptCount").value(0))
                .andExpect(jsonPath("$.planId").doesNotExist())
                .andExpect(jsonPath("$.progress")
                        .doesNotExist())
                .andExpect(jsonPath("$.horizonStart").isNotEmpty())
                .andExpect(jsonPath("$.horizonEnd").isNotEmpty());

        GenerationRequestView queued = requestService.unfinishedJobOf(student.id()).orElseThrow();
        worker.runOnce();

        mockMvc.perform(get(GENERATION_REQUESTS + "/" + queued.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.planId").isNotEmpty())
                .andExpect(jsonPath("$.failureReason").doesNotExist());

        UUID planId = requestService.require(student.id(), queued.id()).planId();
        mockMvc.perform(get("/api/v1/study-plans/" + planId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void askingBeforeThereIsAnythingToPlanSaysSo() throws Exception {
        Account student = student();

        mockMvc.perform(post(GENERATION_REQUESTS)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(student)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:sinapse:problem:setup-incomplete"))
                .andExpect(jsonPath("$.detail")
                        .value(org.hamcrest.Matchers.containsString("at least one goal")));
    }

    @Test
    void aSecondRequestWhileOneIsRunningIsRefusedClearly() throws Exception {
        Account student = studentReadyToPlan(2);
        String token = tokenFor(student);
        mockMvc.perform(post(GENERATION_REQUESTS)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isCreated());

        mockMvc.perform(post(GENERATION_REQUESTS)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                // A clear message, not a constraint violation.
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:sinapse:problem:conflict"));
    }

    @Test
    void aFailureIsReportedAsAKindAndNeverAsAMessage() throws Exception {
        Account student = studentReadyToPlan(2);
        String token = tokenFor(student);
        core.alwaysFail(() -> new CoreProtocolException("core at http://core.invalid returned []"));

        GenerationRequestView job = generate(student);

        mockMvc.perform(get(GENERATION_REQUESTS + "/" + job.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value("CORE_REJECTED"))
                .andExpect(jsonPath("$.planId").doesNotExist());
    }

    @Test
    void aJobOfAnotherAccountIsNotReachable() throws Exception {
        Account owner = studentReadyToPlan(2);
        Account other = student();
        GenerationRequestView job = requestService.queue(owner.id(), null);

        mockMvc.perform(get(GENERATION_REQUESTS + "/" + job.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(other)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:sinapse:problem:resource-not-found"));
    }

    /**
     * The route ADR 0009 names as needing a limit from the start.
     *
     * <p>Each run costs minutes of CPU. The partial index stops a student from having two jobs
     * at once; this stops them from cycling through them, which is why the refused requests
     * still spend the window — the limit is applied in the filter chain, before anything looks
     * at whether the request would have been accepted.
     */
    @Test
    void askingForPlansRepeatedlyIsRateLimited() throws Exception {
        Account student = studentReadyToPlan(2);
        String token = tokenFor(student);

        mockMvc.perform(post(GENERATION_REQUESTS)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isCreated());
        mockMvc.perform(post(GENERATION_REQUESTS)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict());
        mockMvc.perform(post(GENERATION_REQUESTS)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict());

        mockMvc.perform(post(GENERATION_REQUESTS)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.type").value("urn:sinapse:problem:rate-limit-exceeded"));

        // The limit is keyed by account: one student spending their window does not stop
        // another from asking.
        mockMvc.perform(post(GENERATION_REQUESTS)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer "
                                + tokenFor(studentReadyToPlan(2))))
                .andExpect(status().isCreated());
    }
}
