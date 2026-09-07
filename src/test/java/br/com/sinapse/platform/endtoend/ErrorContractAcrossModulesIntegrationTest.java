package br.com.sinapse.platform.endtoend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.sinapse.platform.identity.internal.domain.Account;
import br.com.sinapse.platform.readmodel.support.ReadModelIntegrationTest;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * ADR 0009 across the whole surface, not only the bootstrap probe.
 *
 * <p>The probe proves the mechanism is wired; it cannot prove that every module actually uses it.
 * A module that answered a plain Spring error page, or a 500 with a stack trace, would pass every
 * one of its own tests — none of them asserts the shape of somebody else's failure. So this
 * samples one real error path per module that has an HTTP surface, and asserts the same three
 * things each time: the media type, a type from the closed catalogue, and a {@code detail} that
 * names nothing internal.
 */
class ErrorContractAcrossModulesIntegrationTest extends ReadModelIntegrationTest {

    /** Fragments that must never appear in a body: entity names, columns, SQL, identifiers. */
    private static final String[] MUST_NOT_LEAK = {
            "Exception", "org.springframework", "br.com.sinapse", "select ", "insert ",
            "constraint", "_id", "Entity"};

    @Autowired
    private MockMvc mockMvc;

    @Test
    void identityAnswersTheSharedContract() throws Exception {
        Account holder = student();
        assertContract(mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"definitely-not-the-password"}"""
                                .formatted(holder.email())))
                .andExpect(status().isUnauthorized()));
    }

    @Test
    void educationalAnswersTheSharedContract() throws Exception {
        Account teacher = teacherAccount();
        assertContract(mockMvc.perform(get("/api/v1/classrooms/" + UUID.randomUUID() + "/invites")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(teacher)))
                .andExpect(status().isNotFound()));
    }

    @Test
    void learningRecordAnswersTheSharedContract() throws Exception {
        Account holder = student();
        assertContract(mockMvc.perform(post("/api/v1/study-sessions/" + UUID.randomUUID() + "/completion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(holder))
                        .content("{\"recallRating\":\"GOOD\",\"actualDurationMinutes\":30}"))
                .andExpect(status().isNotFound()));
    }

    @Test
    void planningAnswersTheSharedContract() throws Exception {
        Account holder = student();
        assertContract(mockMvc.perform(get("/api/v1/study-plans/" + UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(holder)))
                .andExpect(status().isNotFound()));
    }

    /** The orchestration layer refusing a student who has said nothing to plan (decision F2). */
    @Test
    void planningOrchestrationAnswersTheSharedContract() throws Exception {
        Account holder = student();
        assertContract(mockMvc.perform(post("/api/v1/study-plans/generation-requests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(holder)))
                .andExpect(status().isConflict()));
    }

    @Test
    void dataRightsAnswersTheSharedContract() throws Exception {
        Account holder = student();
        assertContract(mockMvc.perform(post("/api/v1/me/erasure-requests/" + UUID.randomUUID()
                        + "/cancellation")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(holder)))
                .andExpect(status().isNotFound()));
    }

    @Test
    void readModelsAnswerTheSharedContract() throws Exception {
        Account holder = student();
        assertContract(mockMvc.perform(get("/api/v1/me/agenda")
                        .param("from", clock.instant().toString())
                        .param("to", clock.instant().minusSeconds(1).toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(holder)))
                .andExpect(status().isBadRequest()));
    }

    /**
     * An unauthenticated call to a protected route, which is answered by the security chain
     * rather than by any controller — the one error path no module owns.
     */
    @Test
    void theSecurityChainAnswersTheSharedContract() throws Exception {
        assertContract(mockMvc.perform(get("/api/v1/me/state"))
                .andExpect(status().isUnauthorized()));
    }

    private static void assertContract(ResultActions response) throws Exception {
        response.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(Matchers.startsWith("urn:sinapse:problem:")))
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.status").isNumber())
                .andExpect(jsonPath("$.detail").isNotEmpty());

        for (String forbidden : MUST_NOT_LEAK) {
            response.andExpect(jsonPath("$.detail")
                    .value(Matchers.not(Matchers.containsString(forbidden))));
        }
    }
}
