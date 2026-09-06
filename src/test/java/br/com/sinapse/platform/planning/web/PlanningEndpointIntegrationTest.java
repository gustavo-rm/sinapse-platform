package br.com.sinapse.platform.planning.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.sinapse.platform.curriculum.api.SubjectView;
import br.com.sinapse.platform.curriculum.api.TopicView;
import br.com.sinapse.platform.identity.internal.domain.Account;
import br.com.sinapse.platform.planning.api.StudyPlanView;
import br.com.sinapse.platform.planning.support.PlanningIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** The module over HTTP: who may call what, and what the answers look like. */
class PlanningEndpointIntegrationTest extends PlanningIntegrationTest {

    private static final String AVAILABILITY = "/api/v1/availability";
    private static final String GOALS = "/api/v1/goals";
    private static final String STUDY_PLANS = "/api/v1/study-plans";
    private static final String PLANNED_SESSIONS = "/api/v1/planned-sessions";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void anAnonymousCallerReachesNothing() throws Exception {
        mockMvc.perform(get(AVAILABILITY)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(AVAILABILITY).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(GOALS)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(STUDY_PLANS)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(STUDY_PLANS + "/current")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(PLANNED_SESSIONS + "?from=2026-09-01T00:00:00Z&to=2026-09-02T00:00:00Z"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aStudentDeclaresTheirWeekAndThenClosesAWindow() throws Exception {
        Account student = student();
        String token = tokenFor(student);

        MvcResult declared = mockMvc.perform(post(AVAILABILITY)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dayOfWeek":"TUESDAY","startTime":"19:00","endTime":"21:00",
                                 "effectiveFrom":"2026-01-01"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dayOfWeek").value("TUESDAY"))
                .andExpect(jsonPath("$.startTime").value("19:00:00"))
                .andExpect(jsonPath("$.effectiveUntil").doesNotExist())
                .andReturn();

        UUID windowId = UUID.fromString(JsonPath.read(bodyOf(declared), "$.id"));

        mockMvc.perform(post(AVAILABILITY + "/" + windowId + "/closure")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"effectiveUntil\":\"2026-06-30\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.effectiveUntil").value("2026-06-30"));

        mockMvc.perform(get(AVAILABILITY).param("on", "2026-09-01")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get(AVAILABILITY)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void anOverlappingWindowIsRefusedOverHttp() throws Exception {
        Account student = student();
        availability.declare(student.id(), DayOfWeek.TUESDAY, LocalTime.parse("19:00"),
                LocalTime.parse("21:00"), LocalDate.parse("2026-01-01"), null);

        mockMvc.perform(post(AVAILABILITY)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dayOfWeek":"TUESDAY","startTime":"20:00","endTime":"22:00",
                                 "effectiveFrom":"2026-01-01"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:sinapse:problem:conflict"));
    }

    @Test
    void aWindowThatClosesBeforeItOpensIsRefusedBeforeAnythingIsWritten() throws Exception {
        Account student = student();

        mockMvc.perform(post(AVAILABILITY)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dayOfWeek":"TUESDAY","startTime":"21:00","endTime":"19:00",
                                 "effectiveFrom":"2026-01-01"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:sinapse:problem:validation-failed"))
                .andExpect(jsonPath("$.errors[0].field").value("timeRangeOrdered"));

        assertThat(directory.availabilityOf(student.id())).isEmpty();
    }

    @Test
    void aStudentSetsRevisesAndClosesAGoal() throws Exception {
        Account student = student();
        String token = tokenFor(student);
        SubjectView subject = subject();

        MvcResult set = mockMvc.perform(post(GOALS)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectId\":\"" + subject.id() + "\",\"priority\":4}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.priority").value(4))
                .andReturn();

        UUID goalId = UUID.fromString(JsonPath.read(bodyOf(set), "$.id"));

        mockMvc.perform(patch(GOALS + "/" + goalId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetDate\":\"2026-12-01\",\"priority\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetDate").value("2026-12-01"))
                .andExpect(jsonPath("$.priority").value(5))
                .andExpect(jsonPath("$.subjectId").value(subject.id().toString()));

        mockMvc.perform(post(GOALS + "/" + goalId + "/abandonment")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ABANDONED"))
                .andExpect(jsonPath("$.closedAt").isNotEmpty());

        mockMvc.perform(get(GOALS).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("ABANDONED"));
    }

    @Test
    void aSecondActiveGoalForTheSameSubjectIsRefusedOverHttp() throws Exception {
        Account student = student();
        String token = tokenFor(student);
        SubjectView subject = subject();
        String body = "{\"subjectId\":\"" + subject.id() + "\"}";

        mockMvc.perform(post(GOALS).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.priority").value(3));

        mockMvc.perform(post(GOALS).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:sinapse:problem:conflict"));
    }

    /**
     * The absence the prompt for this module asks for, asserted rather than assumed.
     *
     * <p>A plan comes from the generation job and from nowhere else. One written by hand would
     * have no snapshot, no core version, no parameters and no seed behind it, and could never
     * be regenerated — which is the one property ADR 0007 exists to protect.
     */
    @Test
    void thereIsNoRouteThatCreatesOrEditsAPlan() throws Exception {
        Account student = student();
        String token = tokenFor(student);
        StudyPlanView plan = plan(student, topic().id(), 1);

        mockMvc.perform(post(STUDY_PLANS)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"horizonStart\":\"2026-09-01\"}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.type").value("urn:sinapse:problem:method-not-allowed"));

        mockMvc.perform(patch(STUDY_PLANS + "/" + plan.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"horizonEnd\":\"2027-01-01\"}"))
                .andExpect(status().isMethodNotAllowed());

        mockMvc.perform(post(STUDY_PLANS + "/" + plan.id() + "/sessions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void aStudentReadsTheirPlanItsSessionsAndItsHistory() throws Exception {
        Account student = student();
        String token = tokenFor(student);
        TopicView topic = topic();

        mockMvc.perform(get(STUDY_PLANS + "/current")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:sinapse:problem:resource-not-found"));

        StudyPlanView first = plan(student, topic.id(), 2);
        StudyPlanView second = plan(student, topic.id(), 3);

        mockMvc.perform(get(STUDY_PLANS + "/current")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(second.id().toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.fitness.coverage").value(0.8));

        mockMvc.perform(get(STUDY_PLANS).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(second.id().toString()))
                .andExpect(jsonPath("$[1].status").value("SUPERSEDED"))
                .andExpect(jsonPath("$[1].supersededByPlanId").value(second.id().toString()));

        mockMvc.perform(get(STUDY_PLANS + "/" + first.id() + "/sessions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void aPlanOfAnotherAccountIsNotReachableOverHttp() throws Exception {
        Account owner = student();
        Account other = student();
        StudyPlanView plan = plan(owner, topic().id(), 1);

        mockMvc.perform(get(STUDY_PLANS + "/" + plan.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(other)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:sinapse:problem:resource-not-found"));
        mockMvc.perform(get(STUDY_PLANS + "/" + plan.id() + "/sessions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(other)))
                .andExpect(status().isNotFound());
    }

    @Test
    void aScheduleNeedsAWindowAndTheWindowHasACeiling() throws Exception {
        Account student = student();
        String token = tokenFor(student);
        plan(student, topic().id(), 2);
        Instant now = clock.instant();

        mockMvc.perform(get(PLANNED_SESSIONS).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get(PLANNED_SESSIONS)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("from", now.toString())
                        .param("to", now.plus(Duration.ofDays(31)).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:sinapse:problem:time-window-invalid"));

        mockMvc.perform(get(PLANNED_SESSIONS)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("from", now.toString())
                        .param("to", now.plus(Duration.ofDays(7)).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].kind").value("STUDY"))
                .andExpect(jsonPath("$[0].durationMinutes").value(50));
    }

    private static String bodyOf(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
