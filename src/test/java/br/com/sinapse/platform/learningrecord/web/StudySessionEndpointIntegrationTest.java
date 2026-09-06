package br.com.sinapse.platform.learningrecord.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.sinapse.platform.curriculum.api.TopicView;
import br.com.sinapse.platform.identity.internal.domain.Account;
import br.com.sinapse.platform.learningrecord.api.RecallRating;
import br.com.sinapse.platform.learningrecord.api.SessionKind;
import br.com.sinapse.platform.learningrecord.api.StudySessionView;
import br.com.sinapse.platform.learningrecord.support.LearningRecordIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** The module over HTTP: who may call what, and what the answers look like. */
class StudySessionEndpointIntegrationTest extends LearningRecordIntegrationTest {

    private static final String SESSIONS = "/api/v1/study-sessions";
    private static final String RETROACTIVE = SESSIONS + "/retroactive-entries";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void anAnonymousCallerReachesNothing() throws Exception {
        mockMvc.perform(post(SESSIONS).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(SESSIONS + "/current")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(SESSIONS + "?from=2026-09-01T00:00:00Z&to=2026-09-02T00:00:00Z"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(RETROACTIVE).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aStudentStartsASessionResumesItAndClosesIt() throws Exception {
        Account student = student();
        String token = tokenFor(student);
        TopicView topic = topic();

        MvcResult started = mockMvc.perform(post(SESSIONS)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":\"" + topic.id() + "\",\"kind\":\"STUDY\","
                                + "\"plannedDurationMinutes\":50}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.source").value("SELF_DIRECTED"))
                .andExpect(jsonPath("$.plannedSessionId").doesNotExist())
                .andReturn();

        UUID sessionId = UUID.fromString(JsonPath.read(bodyOf(started), "$.id"));

        mockMvc.perform(get(SESSIONS + "/current")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(sessionId.toString()));

        mockMvc.perform(post(SESSIONS + "/" + sessionId + "/completion")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recallRating\":\"GOOD\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.durationSource").value("MEASURED"))
                .andExpect(jsonPath("$.recallRating").value("GOOD"));

        mockMvc.perform(get(SESSIONS + "/current")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:sinapse:problem:resource-not-found"));
    }

    @Test
    void aSecondSessionIsRefusedWhileOneIsRunning() throws Exception {
        Account student = student();
        String token = tokenFor(student);
        sessions.start(student.id(), topic().id(), null, SessionKind.STUDY, 50);

        mockMvc.perform(post(SESSIONS)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":\"" + topic().id() + "\",\"kind\":\"STUDY\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:sinapse:problem:conflict"));
    }

    @Test
    void aSessionOfAnotherAccountIsNotReachable() throws Exception {
        Account owner = student();
        Account other = student();
        StudySessionView session = sessions.start(owner.id(), topic().id(), null,
                SessionKind.STUDY, 50);

        mockMvc.perform(post(SESSIONS + "/" + session.id() + "/abandonment")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(other)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:sinapse:problem:resource-not-found"));
    }

    @Test
    void aHistoryNeedsAWindowAndTheWindowHasACeiling() throws Exception {
        Account student = student();
        String token = tokenFor(student);
        recordedSession(student, topic().id(), 200, 30, RecallRating.GOOD);
        Instant now = clock.instant();

        mockMvc.perform(get(SESSIONS).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get(SESSIONS)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("from", now.minus(Duration.ofDays(31)).toString())
                        .param("to", now.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:sinapse:problem:time-window-invalid"));

        mockMvc.perform(get(SESSIONS)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("from", now.toString())
                        .param("to", now.minus(Duration.ofDays(1)).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:sinapse:problem:time-window-invalid"));

        mockMvc.perform(get(SESSIONS)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("from", now.minus(Duration.ofDays(1)).toString())
                        .param("to", now.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].durationSource").value("SELF_REPORTED"));
    }

    @Test
    void aRetroactiveEntryIsAcceptedAndRateLimitedPerAccount() throws Exception {
        Account student = student();
        String token = tokenFor(student);
        TopicView topic = topic();
        String body = "{\"topicId\":\"" + topic.id() + "\",\"kind\":\"REVISION\",\"startedAt\":\""
                + clock.instant().minus(Duration.ofHours(3)) + "\",\"actualDurationMinutes\":45,"
                + "\"recallRating\":\"HARD\"}";

        mockMvc.perform(retroactive(token, body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.durationSource").value("SELF_REPORTED"))
                .andExpect(jsonPath("$.actualDurationMinutes").value(45));

        // The test profile allows three in a window. Two more spend it, and the fourth is
        // refused before the request reaches anything that would write a row.
        mockMvc.perform(retroactive(token, body)).andExpect(status().isCreated());
        mockMvc.perform(retroactive(token, body)).andExpect(status().isCreated());

        mockMvc.perform(retroactive(token, body))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.type").value("urn:sinapse:problem:rate-limit-exceeded"));

        // The limit is keyed by account: one student spending their window does not stop
        // another from recording anything.
        mockMvc.perform(retroactive(tokenFor(student()), body))
                .andExpect(status().isCreated());
    }

    @Test
    void aRetroactiveEntryOlderThanTheAcceptedRangeIsRefused() throws Exception {
        Account student = student();
        TopicView topic = topic();

        mockMvc.perform(retroactive(tokenFor(student),
                        "{\"topicId\":\"" + topic.id() + "\",\"kind\":\"STUDY\",\"startedAt\":\""
                                + clock.instant().minus(Duration.ofDays(60))
                                + "\",\"actualDurationMinutes\":45,\"recallRating\":\"GOOD\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:sinapse:problem:time-window-invalid"));
    }

    @Test
    void aRequestMissingWhatASessionNeedsIsRefusedBeforeAnythingIsWritten() throws Exception {
        Account student = student();

        mockMvc.perform(post(SESSIONS)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"STUDY\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:sinapse:problem:validation-failed"))
                .andExpect(jsonPath("$.errors[0].field").value("topicId"));
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            retroactive(String token, String body) {

        return post(RETROACTIVE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private static String bodyOf(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
