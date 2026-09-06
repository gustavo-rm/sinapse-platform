package br.com.sinapse.platform.readmodel.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.sinapse.platform.curriculum.api.TopicView;
import br.com.sinapse.platform.educational.api.ClassroomView;
import br.com.sinapse.platform.identity.internal.domain.Account;
import br.com.sinapse.platform.learningrecord.api.RecallRating;
import br.com.sinapse.platform.planning.api.StudyPlanView;
import br.com.sinapse.platform.readmodel.support.ReadModelIntegrationTest;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

/** The five read models over HTTP, and what each route requires of a caller. */
class ReadModelEndpointIntegrationTest extends ReadModelIntegrationTest {

    private static final String MY_STATE = "/api/v1/me/state";
    private static final String MY_AGENDA = "/api/v1/me/agenda";
    private static final String CLASSROOMS = "/api/v1/classrooms";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void anAnonymousCallerReachesNoneOfThem() throws Exception {
        UUID any = UUID.randomUUID();
        mockMvc.perform(get(MY_STATE)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(MY_AGENDA).param("from", windowStart().toString())
                        .param("to", windowEnd().toString()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/study-plans/" + any + "/summary"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(CLASSROOMS + "/" + any + "/students")
                        .param("from", windowStart().toString())
                        .param("to", windowEnd().toString()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(CLASSROOMS + "/" + any + "/students/" + any + "/panel")
                        .param("from", windowStart().toString())
                        .param("to", windowEnd().toString()))
                .andExpect(status().isUnauthorized());
    }

    /**
     * The classroom reads keep the teacher requirement the educational module declared.
     *
     * <p>They sit under {@code /classrooms/**}, which that module already protects, and this
     * component deliberately does not redeclare the pattern. A read model registering a broader
     * rule of its own could silently replace it — which is what this asserts has not happened.
     */
    @Test
    void aStudentIsNotLetIntoTheClassroomReads() throws Exception {
        Account student = student();
        UUID any = UUID.randomUUID();

        mockMvc.perform(get(CLASSROOMS + "/" + any + "/students")
                        .param("from", windowStart().toString())
                        .param("to", windowEnd().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(student)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:sinapse:problem:access-denied"));

        mockMvc.perform(get(CLASSROOMS + "/" + any + "/students/" + any + "/panel")
                        .param("from", windowStart().toString())
                        .param("to", windowEnd().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(student)))
                .andExpect(status().isForbidden());
    }

    @Test
    void theInitialScreenIsOneCall() throws Exception {
        Account student = studentReadyToPlan(3);
        StudyPlanView plan = planFor(student);

        mockMvc.perform(get(MY_STATE)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.timeZone").value("America/Sao_Paulo"))
                .andExpect(jsonPath("$.setupComplete").value(true))
                .andExpect(jsonPath("$.activePlanId").value(plan.id().toString()))
                .andExpect(jsonPath("$.openSessionId").doesNotExist());
    }

    @Test
    void theAgendaComesBackGroupedByDayWithNamesAndExecutions() throws Exception {
        Account student = studentReadyToPlan(3);
        StudyPlanView plan = planFor(student);
        var slot = directory.sessionsOfPlan(plan.id()).getFirst();
        executed(student, slot.topicId(), slot.id(), Duration.ofHours(2));

        mockMvc.perform(get(MY_AGENDA)
                        .param("from", windowStart().toString())
                        .param("to", windowEnd().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days").isNotEmpty())
                .andExpect(jsonPath("$.days[0].date").isNotEmpty())
                .andExpect(jsonPath("$.days[0].entries[0].topicName").isNotEmpty())
                .andExpect(jsonPath("$.days[0].entries[0].subjectName").isNotEmpty())
                .andExpect(jsonPath("$.days[0].entries[0].kind").value("STUDY"));
    }

    /** Enumerations travel as the domain literal in upper case; the API returns no display text. */
    @Test
    void enumerationsTravelAsUppercaseLiterals() throws Exception {
        Account teacher = teacherAccount();
        ClassroomView classroom = classroomOf(teacher);
        Account student = student();
        enrol(student, teacher, classroom);
        TopicView topic = topic();
        studied(student, topic.id(), Duration.ofDays(1), 30, RecallRating.EASY);

        mockMvc.perform(get(CLASSROOMS + "/" + classroom.id() + "/students/" + student.id() + "/panel")
                        .param("from", windowStart().toString())
                        .param("to", windowEnd().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(teacher)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recentSessions[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.recentSessions[0].durationSource").value("MEASURED"))
                .andExpect(jsonPath("$.recentSessions[0].recallRating").value("EASY"))
                .andExpect(jsonPath("$.recallTrajectory[0].points[0].rating").value("EASY"));
    }

    @Test
    void theClassListAnswersTheOwnerAndNobodyElse() throws Exception {
        Account owner = teacherAccount();
        Account other = teacherAccount();
        ClassroomView classroom = classroomOf(owner);
        Account student = student();
        enrol(student, owner, classroom);

        mockMvc.perform(get(CLASSROOMS + "/" + classroom.id() + "/students")
                        .param("from", windowStart().toString())
                        .param("to", windowEnd().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.students.length()").value(1))
                .andExpect(jsonPath("$.students[0].accountId").value(student.id().toString()))
                .andExpect(jsonPath("$.students[0].totalMinutes").value(0));

        mockMvc.perform(get(CLASSROOMS + "/" + classroom.id() + "/students")
                        .param("from", windowStart().toString())
                        .param("to", windowEnd().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(other)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:sinapse:problem:resource-not-found"));
    }

    @Test
    void aWindowTooWideIsRefusedWithTheSharedErrorContract() throws Exception {
        Account student = student();

        mockMvc.perform(get(MY_AGENDA)
                        .param("from", clock.instant().minus(Duration.ofDays(60)).toString())
                        .param("to", clock.instant().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(student)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:sinapse:problem:time-window-invalid"));
    }

    /** The window is mandatory: this API has no generic pagination to fall back on. */
    @Test
    void aHistoryReadWithoutAWindowIsRefused() throws Exception {
        Account student = student();

        mockMvc.perform(get(MY_AGENDA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(student)))
                .andExpect(status().isBadRequest());
    }

    /** A refusal carries nothing about the student it refused to talk about. */
    @Test
    void aRefusalDisclosesNothingAboutWhoWasAskedAbout() throws Exception {
        Account teacher = teacherAccount();
        ClassroomView classroom = classroomOf(teacher);
        Account student = student();
        enrol(student, teacher, classroom);
        revokeSharing(student);

        mockMvc.perform(get(CLASSROOMS + "/" + classroom.id() + "/students/" + student.id() + "/panel")
                        .param("from", windowStart().toString())
                        .param("to", windowEnd().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(teacher)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(
                                student.id().toString()))))
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("consent"))));
    }
}
