package br.com.sinapse.platform.datarights.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.sinapse.platform.datarights.api.ErasureRequestView;
import br.com.sinapse.platform.datarights.support.DataRightsIntegrationTest;
import br.com.sinapse.platform.identity.internal.domain.Account;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** The three rights over HTTP. */
class DataRightsEndpointIntegrationTest extends DataRightsIntegrationTest {

    private static final String ERASURE_REQUESTS = "/api/v1/me/erasure-requests";
    private static final String DATA_EXPORT = "/api/v1/me/data-export";
    private static final String ACCESS_DISCLOSURE = "/api/v1/me/access-disclosure";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void anAnonymousCallerReachesNothing() throws Exception {
        mockMvc.perform(post(ERASURE_REQUESTS)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(ERASURE_REQUESTS)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(DATA_EXPORT)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(ACCESS_DISCLOSURE)).andExpect(status().isUnauthorized());
    }

    /**
     * The flow the window exists for, over the routes the holder actually has.
     *
     * <p>The token is taken <em>after</em> the request, because making one revokes every session
     * the holder had. That they can get a new one is the whole point.
     */
    @Test
    void aHolderAsksToBeErasedAndThenChangesTheirMind() throws Exception {
        Account student = fullyPopulatedStudent();

        MvcResult requested = mockMvc.perform(post(ERASURE_REQUESTS)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(student)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.effectiveAt").isNotEmpty())
                .andExpect(jsonPath("$.completedAt").doesNotExist())
                .andReturn();

        UUID requestId = UUID.fromString(JsonPath.read(bodyOf(requested), "$.id"));
        String tokenAfterSuspension = tokenFor(student);

        mockMvc.perform(get(ERASURE_REQUESTS)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAfterSuspension))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(post(ERASURE_REQUESTS + "/" + requestId + "/cancellation")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAfterSuspension))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledAt").isNotEmpty());
    }

    @Test
    void aSecondRequestIsRefusedClearly() throws Exception {
        Account student = fullyPopulatedStudent();
        String token = tokenFor(student);
        mockMvc.perform(post(ERASURE_REQUESTS).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isCreated());

        mockMvc.perform(post(ERASURE_REQUESTS)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(student)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:sinapse:problem:conflict"));
    }

    @Test
    void aRequestOfAnotherAccountIsNotReachable() throws Exception {
        Account owner = fullyPopulatedStudent();
        Account other = student();
        ErasureRequestView request = erasureRequests.request(owner.id());

        mockMvc.perform(post(ERASURE_REQUESTS + "/" + request.id() + "/cancellation")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(other)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:sinapse:problem:resource-not-found"));
    }

    @Test
    void theExportIsAnAuthenticatedDownload() throws Exception {
        Account student = fullyPopulatedStudent();

        mockMvc.perform(get(DATA_EXPORT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(student)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(jsonPath("$.generatedAt").isNotEmpty())
                .andExpect(jsonPath("$.modules.identity").exists())
                .andExpect(jsonPath("$.modules.learningrecord.studySessions").isNotEmpty())
                .andExpect(jsonPath("$.modules.planning.goals").isNotEmpty());
    }

    /**
     * Article 18 does not stop applying because the holder withdrew their consent.
     */
    @Test
    void aSuspendedHolderCanStillTakeTheirData() throws Exception {
        Account student = fullyPopulatedStudent();
        erasureRequests.request(student.id());

        mockMvc.perform(get(DATA_EXPORT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modules.learningrecord.studySessions").isNotEmpty());
    }

    @Test
    void assemblingTheExportIsRateLimited() throws Exception {
        Account student = fullyPopulatedStudent();
        String token = tokenFor(student);

        for (int attempt = 0; attempt < 3; attempt++) {
            mockMvc.perform(get(DATA_EXPORT).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get(DATA_EXPORT).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.type").value("urn:sinapse:problem:rate-limit-exceeded"));

        // Keyed by account: one holder spending their window does not stop another.
        mockMvc.perform(get(DATA_EXPORT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(student())))
                .andExpect(status().isOk());
    }

    @Test
    void theDisclosureListsWhoCouldReadTheData() throws Exception {
        Account student = fullyPopulatedStudent();

        mockMvc.perform(get(ACCESS_DISCLOSURE)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].teacherName").value("Prof. Exemplo"))
                .andExpect(jsonPath("$[0].from").isNotEmpty())
                .andExpect(jsonPath("$[0].to").doesNotExist());
    }

    private static String bodyOf(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
