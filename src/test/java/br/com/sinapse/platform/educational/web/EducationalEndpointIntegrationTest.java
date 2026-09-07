package br.com.sinapse.platform.educational.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.sinapse.platform.curriculum.api.SubjectView;
import br.com.sinapse.platform.educational.api.ClassroomView;
import br.com.sinapse.platform.educational.internal.domain.Invite;
import br.com.sinapse.platform.educational.support.EducationalIntegrationTest;
import br.com.sinapse.platform.identity.internal.domain.Account;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** The module over HTTP: who may call what, and what a student is shown before deciding. */
class EducationalEndpointIntegrationTest extends EducationalIntegrationTest {

    private static final String CLASSROOMS = "/api/v1/classrooms";
    private static final String INVITES = "/api/v1/invites";
    private static final String ENROLLMENTS = "/api/v1/enrollments";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void aTeacherOpensAClassroomAndIssuesAnInvite() throws Exception {
        Account teacher = teacher("Prof. Exemplo");
        String token = tokenFor(teacher);
        SubjectView subject = subject("Anatomia");

        MvcResult opened = mockMvc.perform(post(CLASSROOMS)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Medicina 2026\",\"subjectIds\":[\"" + subject.id() + "\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.studentCount").value(0))
                .andReturn();

        UUID classroomId = UUID.fromString(JsonPath.read(bodyOf(opened), "$.id"));

        MvcResult issued = mockMvc.perform(post(CLASSROOMS + "/" + classroomId + "/invites")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(true))
                .andReturn();

        String code = JsonPath.read(bodyOf(issued), "$.code");
        assertThat(code)
                .as("ten characters of Crockford base32, which is the alphabet without the "
                        + "letters a reader mistakes for digits")
                .hasSize(10)
                .matches("[0-9A-HJKMNP-TV-Z]{10}");
    }

    @Test
    void aStudentCannotOpenAClassroom() throws Exception {
        Account student = student();

        mockMvc.perform(post(CLASSROOMS)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Minha turma\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:sinapse:problem:access-denied"));
    }

    @Test
    void anAnonymousCallerReachesNothing() throws Exception {
        mockMvc.perform(get(CLASSROOMS)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(ENROLLMENTS)).andExpect(status().isUnauthorized());
        // The preview says what this caller's data would be disclosed to. Answering it to
        // whoever holds a code would make it a directory of classrooms and teacher names for
        // anybody willing to guess.
        mockMvc.perform(get(INVITES + "/ZZZZZZZZZZ/preview"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(INVITES + "/ZZZZZZZZZZ/redemptions"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * The preview, which is the reason this endpoint exists at all.
     *
     * <p>ADR 0005 accepted the cost of integral visibility on one condition: that the student
     * is told what it means at the moment of deciding. The field asserted here is that
     * condition, and without it the consent is not informed.
     */
    @Test
    void thePreviewSaysWhatAcceptingWouldDisclose() throws Exception {
        Account teacher = teacher("Prof. Ana Exemplo");
        SubjectView subject = subject("Anatomia");
        ClassroomView classroom = classroom(teacher, Set.of(subject.id()));
        Invite invite = invite(teacher, classroom);
        Account student = student();

        mockMvc.perform(get(INVITES + "/" + invite.code() + "/preview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classroomName").value(classroom.name()))
                .andExpect(jsonPath("$.teacherName").value("Prof. Ana Exemplo"))
                .andExpect(jsonPath("$.subjectNames[0]").value("Anatomia"))
                .andExpect(jsonPath("$.requiresConsent").value("INSTITUTION_SHARING"))
                .andExpect(jsonPath("$.visibility.scope").value("ALL"))
                .andExpect(jsonPath("$.visibility.description")
                        .value(org.hamcrest.Matchers.containsString("not part of this classroom")))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty());
    }

    @Test
    void thePreviewOfAnUnknownCodeAnswersLikeTheOneOfARevokedCode() throws Exception {
        Account teacher = teacher("Prof. Exemplo");
        Invite invite = invite(teacher, classroom(teacher));
        invites.revoke(teacher.id(), invite.id());
        String token = tokenFor(student());

        MvcResult unknown = mockMvc.perform(get(INVITES + "/ZZZZZZZZZZ/preview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andReturn();
        MvcResult revoked = mockMvc.perform(get(INVITES + "/" + invite.code() + "/preview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andReturn();

        // Everything but "instance", which is the path the caller itself sent and therefore
        // carries the code they already had. What must not differ is the reason.
        assertThat(withoutInstance(bodyOf(unknown)))
                .as("the code is stored in clear; an answer that separated the two would hand a "
                        + "guesser the signal the entropy exists to deny them")
                .isEqualTo(withoutInstance(bodyOf(revoked)));
    }

    @Test
    void aStudentRedeemsAndThenSeesTheirOwnMembership() throws Exception {
        Account teacher = teacher("Prof. Exemplo");
        ClassroomView classroom = classroom(teacher);
        Invite invite = invite(teacher, classroom);
        Account student = student();
        String token = tokenFor(student);

        mockMvc.perform(post(INVITES + "/" + invite.code() + "/redemptions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.classroomId").value(classroom.id().toString()));

        MvcResult listed = mockMvc.perform(get(ENROLLMENTS)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].classroomName").value(classroom.name()))
                .andReturn();

        UUID enrollmentId = UUID.fromString(JsonPath.read(bodyOf(listed), "$[0].id"));
        mockMvc.perform(delete(ENROLLMENTS + "/" + enrollmentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(ENROLLMENTS).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].endedReason").value("STUDENT_LEFT"));
    }

    @Test
    void redeemingWithoutSharingConsentIsRefused() throws Exception {
        Account teacher = teacher("Prof. Exemplo");
        Invite invite = invite(teacher, classroom(teacher));
        Account student = studentWithoutSharingConsent();

        mockMvc.perform(post(INVITES + "/" + invite.code() + "/redemptions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(student)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:sinapse:problem:access-denied"));
    }

    @Test
    void aTeacherSeesTheirClassroomsRosterAndCanRemoveAStudent() throws Exception {
        Account teacher = teacher("Prof. Exemplo");
        String token = tokenFor(teacher);
        ClassroomView classroom = classroom(teacher);
        Account student = student();
        invites.redeem(student.id(), invite(teacher, classroom).code());

        mockMvc.perform(get(CLASSROOMS + "/" + classroom.id() + "/enrollments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].accountId").value(student.id().toString()));

        mockMvc.perform(delete(CLASSROOMS + "/" + classroom.id() + "/students/" + student.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(CLASSROOMS + "/" + classroom.id() + "/enrollments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void aTeacherCannotReachAnotherTeachersClassroom() throws Exception {
        Account owner = teacher("Prof. Dono");
        Account other = teacher("Prof. Alheio");
        ClassroomView classroom = classroom(owner);

        mockMvc.perform(get(CLASSROOMS + "/" + classroom.id() + "/enrollments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(other)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:sinapse:problem:resource-not-found"));
    }

    @Test
    void archivingOverHttpEndsTheEnrollmentsAndRevokesTheInvites() throws Exception {
        Account teacher = teacher("Prof. Exemplo");
        String token = tokenFor(teacher);
        ClassroomView classroom = classroom(teacher);
        Invite invite = invite(teacher, classroom);
        invites.redeem(student().id(), invite.code());

        mockMvc.perform(post(CLASSROOMS + "/" + classroom.id() + "/archival")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));

        assertThat(activeEnrollmentCount(classroom.id())).isZero();
        mockMvc.perform(get(CLASSROOMS + "/" + classroom.id() + "/invites")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$[0].active").value(false));
    }

    private static String bodyOf(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    /** The problem body without its {@code instance} member. */
    private static Map<String, Object> withoutInstance(String body) {
        Map<String, Object> problem = new LinkedHashMap<>(JsonPath.read(body, "$"));
        problem.remove("instance");
        return problem;
    }
}
