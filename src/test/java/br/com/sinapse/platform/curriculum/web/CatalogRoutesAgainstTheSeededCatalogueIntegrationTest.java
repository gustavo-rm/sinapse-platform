package br.com.sinapse.platform.curriculum.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.sinapse.platform.curation.internal.CatalogApplier;
import br.com.sinapse.platform.curation.internal.CatalogFiles;
import br.com.sinapse.platform.curation.internal.model.DesiredCatalogue;
import br.com.sinapse.platform.planning.support.PlanningIntegrationTest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The catalogue routes against the example catalogue under {@code catalog/}, loaded the way
 * production loads it, and the assumption a client builds on them: an identifier read here is
 * one the rest of the API accepts.
 *
 * <p>A goal is set against a subject, not a topic (decision L3), so every subject identifier is
 * offered to goal creation. The route that takes a topic identifier is starting a study session,
 * so every topic identifier is offered to that.
 */
class CatalogRoutesAgainstTheSeededCatalogueIntegrationTest extends PlanningIntegrationTest {

    private static final String SUBJECTS = "/api/v1/subjects";

    private static final TypeReference<List<Map<String, Object>>> ITEMS = new TypeReference<>() { };

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CatalogApplier applier;

    private String bearer;

    @BeforeEach
    void seedTheCatalogueAndSignIn() {
        DesiredCatalogue desired = CatalogFiles.read(Path.of("catalog"), null);
        assertThat(desired.problems()).isEmpty();
        applier.apply(desired, "catalog-routes-test", false);
        bearer = "Bearer " + tokenFor(student());
    }

    @Test
    void everyTopicInTheDatabaseIsReachableThroughItsSubject() throws Exception {
        List<String> topicIds = new ArrayList<>();
        for (Map<String, Object> subject : subjects()) {
            topicIds.addAll(topicIdsOf((String) subject.get("id")));
        }

        assertThat(topicIds)
                .as("the routes return the whole catalogue, once")
                .doesNotHaveDuplicates()
                .hasSize(jdbc.queryForObject("select count(*) from topic", Integer.class));
    }

    @Test
    void everySubjectReturnedIsAcceptedWhenSettingAGoal() throws Exception {
        List<Map<String, Object>> subjects = subjects();
        assertThat(subjects).isNotEmpty();

        for (Map<String, Object> subject : subjects) {
            mockMvc.perform(post("/api/v1/goals")
                            .header(HttpHeaders.AUTHORIZATION, bearer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"subjectId\":\"" + subject.get("id") + "\"}"))
                    .andExpect(status().isCreated());
        }
    }

    @Test
    void everyTopicReturnedIsAcceptedWhenStartingAStudySession() throws Exception {
        for (Map<String, Object> subject : subjects()) {
            List<String> topicIds = topicIdsOf((String) subject.get("id"));
            assertThat(topicIds).isNotEmpty();

            for (String topicId : topicIds) {
                MvcResult started = mockMvc.perform(post("/api/v1/study-sessions")
                                .header(HttpHeaders.AUTHORIZATION, bearer)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"topicId\":\"" + topicId
                                        + "\",\"kind\":\"STUDY\",\"plannedDurationMinutes\":25}"))
                        .andExpect(status().isCreated())
                        .andReturn();
                // One session in progress at a time, so each is closed before the next.
                String sessionId = JsonPath.read(
                        started.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id");
                mockMvc.perform(post("/api/v1/study-sessions/" + sessionId + "/abandonment")
                                .header(HttpHeaders.AUTHORIZATION, bearer))
                        .andExpect(status().isOk());
            }
        }
    }

    private List<Map<String, Object>> subjects() throws Exception {
        return items(mockMvc.perform(get(SUBJECTS).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andReturn());
    }

    private List<String> topicIdsOf(String subjectId) throws Exception {
        return items(mockMvc.perform(get(SUBJECTS + "/" + subjectId + "/topics")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andReturn())
                .stream()
                .map(topic -> (String) topic.get("id"))
                .toList();
    }

    private List<Map<String, Object>> items(MvcResult result) throws Exception {
        return objectMapper.readValue(result.getResponse().getContentAsByteArray(), ITEMS);
    }
}
