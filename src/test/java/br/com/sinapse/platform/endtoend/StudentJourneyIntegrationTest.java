package br.com.sinapse.platform.endtoend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.sinapse.platform.curriculum.api.SubjectView;
import br.com.sinapse.platform.curriculum.api.TopicView;
import br.com.sinapse.platform.identity.api.AccountStatus;
import br.com.sinapse.platform.identity.api.ConsentPurpose;
import br.com.sinapse.platform.identity.internal.domain.Account;
import br.com.sinapse.platform.identity.internal.domain.AccountTokenPurpose;
import br.com.sinapse.platform.identity.support.CapturingAccountNotifier;
import br.com.sinapse.platform.identity.support.IdentityFixtures;
import br.com.sinapse.platform.planning.orchestration.PlanGenerationWorker;
import br.com.sinapse.platform.readmodel.support.ReadModelIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The whole product, over HTTP, in one run.
 *
 * <p>No individual prompt could write this: each one had only its own module and the ones below
 * it. It is the check that the pieces fit, rather than that each passes alone — and it is
 * deliberately driven through the routes rather than through the services, because a boundary
 * that only holds when a service is called directly is not a boundary a client will meet.
 *
 * <p>Two things stand in for infrastructure that does not exist in a test: the optimisation core
 * is the stub of {@code OrchestrationTestSupport}, and the notification transport is the
 * capturing notifier, which is how the verification token gets read back. Everything else — the
 * migrations, the triggers, the partial indexes, the rate limiter, the security chain, the
 * access policies — is what would run in production.
 */
class StudentJourneyIntegrationTest extends ReadModelIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CapturingAccountNotifier notifications;

    @Autowired
    private PlanGenerationWorker generationWorker;

    /**
     * Registration through to a closed study session, then the teacher's view of it, then the two
     * withdrawals the whole consent model exists to make real.
     */
    @Test
    void aStudentSignsUpPlansStudiesIsSeenByATeacherAndThenTakesItAllBack() throws Exception {
        // --- 1. Register as an adult, and be told to check the address ------------------------
        String email = identity.uniqueEmail();
        MvcResult registered = mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationBody(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_VERIFICATION"))
                .andReturn();
        UUID studentId = UUID.fromString(JsonPath.read(bodyOf(registered), "$.accountId"));

        // --- 2. Verify the e-mail with the token that went to the address ---------------------
        mockMvc.perform(post("/api/v1/email-verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + notifications.requireToken(studentId,
                                AccountTokenPurpose.EMAIL_VERIFICATION) + "\"}"))
                .andExpect(status().isNoContent());

        String student = signIn(email);
        assertThat(identity.reload(studentId).status()).isEqualTo(AccountStatus.ACTIVE);

        // The initial screen: active, nothing set up, nothing to resume.
        mockMvc.perform(get("/api/v1/me/state").header(HttpHeaders.AUTHORIZATION, student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.setupComplete").value(false))
                .andExpect(jsonPath("$.activePlanId").doesNotExist());

        // --- 3. Consent to the optional purpose, declare availability, set a goal -------------
        mockMvc.perform(post("/api/v1/consents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, student)
                        .content("""
                                {"purpose":"ACADEMIC_RESEARCH","termsVersionId":"%s"}"""
                                .formatted(identity.currentTermsId(ConsentPurpose.ACADEMIC_RESEARCH))))
                .andExpect(status().isCreated());

        SubjectView subject = subject();
        TopicView topic = topicOf(subject);
        topicOf(subject);
        LocalDate today = LocalDate.now(clock);
        for (DayOfWeek day : DayOfWeek.values()) {
            mockMvc.perform(post("/api/v1/availability")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(HttpHeaders.AUTHORIZATION, student)
                            .content("""
                                    {"dayOfWeek":"%s","startTime":"19:00","endTime":"21:00",
                                     "effectiveFrom":"%s"}""".formatted(day, today)))
                    .andExpect(status().isCreated());
        }
        mockMvc.perform(post("/api/v1/goals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, student)
                        .content("{\"subjectId\":\"" + subject.id() + "\",\"priority\":4}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/me/state").header(HttpHeaders.AUTHORIZATION, student))
                .andExpect(jsonPath("$.setupComplete").value(true));

        // --- 4. Ask for a plan, let the worker run, and read the agenda -----------------------
        MvcResult queued = mockMvc.perform(post("/api/v1/study-plans/generation-requests")
                        .header(HttpHeaders.AUTHORIZATION, student))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        UUID jobId = UUID.fromString(JsonPath.read(bodyOf(queued), "$.id"));

        generationWorker.runOnce();

        MvcResult ready = mockMvc.perform(get("/api/v1/study-plans/generation-requests/" + jobId)
                        .header(HttpHeaders.AUTHORIZATION, student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.planId").isNotEmpty())
                .andReturn();
        UUID planId = UUID.fromString(JsonPath.read(bodyOf(ready), "$.planId"));

        MvcResult agenda = mockMvc.perform(get("/api/v1/me/agenda")
                        .param("from", windowStart().toString())
                        .param("to", windowEnd().toString())
                        .header(HttpHeaders.AUTHORIZATION, student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days").isNotEmpty())
                .andExpect(jsonPath("$.days[0].entries[0].topicName").isNotEmpty())
                .andExpect(jsonPath("$.days[0].entries[0].execution").doesNotExist())
                .andReturn();
        UUID firstSlot = UUID.fromString(
                JsonPath.read(bodyOf(agenda), "$.days[0].entries[0].plannedSessionId"));
        UUID firstTopic = UUID.fromString(
                JsonPath.read(bodyOf(agenda), "$.days[0].entries[0].topicId"));

        // --- 5. Study: open a session against the slot, close it with a rating ----------------
        MvcResult started = mockMvc.perform(post("/api/v1/study-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, student)
                        .content("""
                                {"topicId":"%s","plannedSessionId":"%s","kind":"STUDY",
                                 "plannedDurationMinutes":50}""".formatted(firstTopic, firstSlot)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.source").value("FROM_PLAN"))
                .andReturn();
        UUID sessionId = UUID.fromString(JsonPath.read(bodyOf(started), "$.id"));

        mockMvc.perform(get("/api/v1/me/state").header(HttpHeaders.AUTHORIZATION, student))
                .andExpect(jsonPath("$.openSessionId").value(sessionId.toString()));

        mockMvc.perform(post("/api/v1/study-sessions/" + sessionId + "/completion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, student)
                        .content("{\"recallRating\":\"GOOD\",\"actualDurationMinutes\":45}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.durationSource").value("SELF_REPORTED"));

        // The agenda now shows the execution against the slot it executed.
        mockMvc.perform(get("/api/v1/me/agenda")
                        .param("from", windowStart().toString())
                        .param("to", windowEnd().toString())
                        .header(HttpHeaders.AUTHORIZATION, student))
                .andExpect(jsonPath("$.days[0].entries[0].execution.sessionId")
                        .value(sessionId.toString()))
                .andExpect(jsonPath("$.days[0].entries[0].execution.recallRating").value("GOOD"));

        mockMvc.perform(get("/api/v1/study-plans/" + planId + "/summary")
                        .header(HttpHeaders.AUTHORIZATION, student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPlannedMinutes").isNumber());

        // --- 6. A teacher opens a classroom and invites the student ---------------------------
        Account teacherAccount = teacherAccount();
        String teacher = signIn(identity.reload(teacherAccount.id()).email());

        MvcResult classroom = mockMvc.perform(post("/api/v1/classrooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, teacher)
                        .content("{\"name\":\"Turma da jornada\",\"subjectIds\":[\""
                                + subject.id() + "\"]}"))
                .andExpect(status().isCreated())
                .andReturn();
        UUID classroomId = UUID.fromString(JsonPath.read(bodyOf(classroom), "$.id"));

        MvcResult invite = mockMvc.perform(post("/api/v1/classrooms/" + classroomId + "/invites")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, teacher)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andReturn();
        String code = JsonPath.read(bodyOf(invite), "$.code");

        // --- 7. The student reads the preview before deciding, then redeems --------------------
        mockMvc.perform(get("/api/v1/invites/" + code + "/preview")
                        .header(HttpHeaders.AUTHORIZATION, student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classroomName").value("Turma da jornada"))
                .andExpect(jsonPath("$.teacherName").value("Prof. Exemplo"))
                .andExpect(jsonPath("$.visibility.scope").value("ALL"))
                .andExpect(jsonPath("$.visibility.description").isNotEmpty())
                .andExpect(jsonPath("$.requiresConsent").value("INSTITUTION_SHARING"));

        mockMvc.perform(post("/api/v1/consents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, student)
                        .content("""
                                {"purpose":"INSTITUTION_SHARING","termsVersionId":"%s"}"""
                                .formatted(identity.currentTermsId(ConsentPurpose.INSTITUTION_SHARING))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/invites/" + code + "/redemptions")
                        .header(HttpHeaders.AUTHORIZATION, student))
                .andExpect(status().isCreated());

        // --- 8. The teacher can now read the student -------------------------------------------
        mockMvc.perform(get("/api/v1/classrooms/" + classroomId + "/students")
                        .param("from", windowStart().toString())
                        .param("to", windowEnd().toString())
                        .header(HttpHeaders.AUTHORIZATION, teacher))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.students.length()").value(1))
                .andExpect(jsonPath("$.students[0].totalMinutes").value(45));

        mockMvc.perform(get("/api/v1/classrooms/" + classroomId + "/students/" + studentId + "/panel")
                        .param("from", windowStart().toString())
                        .param("to", windowEnd().toString())
                        .header(HttpHeaders.AUTHORIZATION, teacher))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minutesBySubject[0].minutes").value(45))
                .andExpect(jsonPath("$.recallTrajectory[0].points[0].rating").value("GOOD"));

        // --- 9. The student withdraws sharing; the teacher loses them on the next request ------
        mockMvc.perform(delete("/api/v1/consents/INSTITUTION_SHARING")
                        .header(HttpHeaders.AUTHORIZATION, student))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/classrooms/" + classroomId + "/students")
                        .param("from", windowStart().toString())
                        .param("to", windowEnd().toString())
                        .header(HttpHeaders.AUTHORIZATION, teacher))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.students.length()").value(0));

        mockMvc.perform(get("/api/v1/classrooms/" + classroomId + "/students/" + studentId + "/panel")
                        .param("from", windowStart().toString())
                        .param("to", windowEnd().toString())
                        .header(HttpHeaders.AUTHORIZATION, teacher))
                .andExpect(status().isNotFound());

        assertThat(educational.activeEnrollmentsOf(java.util.List.of(studentId)))
                .as("the membership is untouched: withdrawing consent is not leaving the classroom")
                .hasSize(1);

        // --- 10. The student asks to be erased, then changes their mind within the window ------
        MvcResult erasure = mockMvc.perform(post("/api/v1/me/erasure-requests")
                        .header(HttpHeaders.AUTHORIZATION, student))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andReturn();
        UUID requestId = UUID.fromString(JsonPath.read(bodyOf(erasure), "$.id"));

        assertThat(identity.reload(studentId).status()).isEqualTo(AccountStatus.SUSPENDED);
        assertThat(openSessionsOf(studentId))
                .as("asking to be erased revokes every session in the same transaction")
                .isZero();

        // Signing in still works during the window: that is what makes it reversible.
        String afterSuspension = signIn(email);
        mockMvc.perform(post("/api/v1/me/erasure-requests/" + requestId + "/cancellation")
                        .header(HttpHeaders.AUTHORIZATION, afterSuspension))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // --- 11. Everything is back, and nothing was lost --------------------------------------
        assertThat(identity.reload(studentId).status()).isEqualTo(AccountStatus.ACTIVE);
        String restored = signIn(email);
        mockMvc.perform(get("/api/v1/me/state").header(HttpHeaders.AUTHORIZATION, restored))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.activePlanId").value(planId.toString()))
                .andExpect(jsonPath("$.setupComplete").value(true));

        mockMvc.perform(get("/api/v1/study-sessions")
                        .param("from", windowStart().toString())
                        .param("to", windowEnd().toString())
                        .header(HttpHeaders.AUTHORIZATION, restored))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(sessionId.toString()));

        assertThat(topic).isNotNull();
    }

    /**
     * Every path that suspends an account revokes its sessions in the same transaction.
     *
     * <p>Section 9.5 and ADR 0010. It matters far beyond identity: every module's access check
     * asks whether the account is active, and a session that outlived the suspension would carry
     * a caller past all of them.
     */
    @Test
    void everyPathThatSuspendsAnAccountRevokesItsSessionsAtOnce() throws Exception {
        Account byConsent = student();
        tokenFor(byConsent);
        assertThat(openSessionsOf(byConsent.id())).isEqualTo(1);
        consents.revoke(byConsent.id(), ConsentPurpose.LEARNING_DATA_PROCESSING);
        assertThat(openSessionsOf(byConsent.id()))
                .as("withdrawing an essential consent suspends and revokes together")
                .isZero();

        Account bySuspension = student();
        tokenFor(bySuspension);
        assertThat(openSessionsOf(bySuspension.id())).isEqualTo(1);
        consents.suspend(bySuspension.id());
        assertThat(openSessionsOf(bySuspension.id())).isZero();

        Account byErasure = student();
        String token = tokenFor(byErasure);
        assertThat(openSessionsOf(byErasure.id())).isEqualTo(1);
        mockMvc.perform(post("/api/v1/me/erasure-requests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isCreated());
        assertThat(openSessionsOf(byErasure.id())).isZero();
    }

    private int openSessionsOf(UUID accountId) {
        return jdbc.queryForObject(
                "select count(*) from user_session where account_id = ? and revoked_at is null",
                Integer.class, accountId);
    }

    private String signIn(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}"""
                                .formatted(email, IdentityFixtures.DEFAULT_PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn();
        return "Bearer " + JsonPath.read(bodyOf(result), "$.token");
    }

    private String registrationBody(String email) {
        return """
                {"email":"%s","password":"%s","dateOfBirth":"%s","timeZone":"%s",
                 "acceptedTerms":[{"purpose":"LEARNING_DATA_PROCESSING","termsVersionId":"%s"}]}
                """.formatted(email, IdentityFixtures.DEFAULT_PASSWORD, identity.adultDateOfBirth(),
                IdentityFixtures.DEFAULT_ZONE,
                identity.currentTermsId(ConsentPurpose.LEARNING_DATA_PROCESSING));
    }

    private static String bodyOf(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
