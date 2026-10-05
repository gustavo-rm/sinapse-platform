package br.com.sinapse.platform.curriculum.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.sinapse.platform.curriculum.api.CatalogCuration;
import br.com.sinapse.platform.curriculum.api.EffortTier;
import br.com.sinapse.platform.curriculum.api.SubjectView;
import br.com.sinapse.platform.curriculum.api.TopicView;
import br.com.sinapse.platform.curriculum.support.CurriculumIntegrationTest;
import br.com.sinapse.platform.identity.internal.service.SessionService;
import br.com.sinapse.platform.identity.support.IdentityFixtures;
import br.com.sinapse.platform.identity.support.IdentityTestSupport;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The catalogue over HTTP: what each route answers, in what order, and with which fields.
 *
 * <p>The field sets are compared as sets of keys, not value by value. A value check stays green
 * when a field is added; a key-set check is what fails when an effort band, a minute estimate
 * or a timestamp leaks onto a student's screen.
 */
@Import(IdentityTestSupport.class)
class CatalogEndpointIntegrationTest extends CurriculumIntegrationTest {

    private static final String SUBJECTS = "/api/v1/subjects";

    /** The only fields either route may return, per item. */
    private static final Set<String> FIELDS = Set.of("id", "name", "position");

    private static final TypeReference<List<Map<String, Object>>> ITEMS = new TypeReference<>() { };

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IdentityFixtures identity;

    @Autowired
    private SessionService sessions;

    private String bearer;

    @BeforeEach
    void signIn() {
        bearer = "Bearer " + sessions.open(identity.activeAdult(identity.uniqueEmail()),
                "203.0.113.7", "integration-test").token();
    }

    @Test
    void subjectsComeBackWithExactlyIdNameAndPositionInCodeOrder() throws Exception {
        SubjectView later = curation.defineSubject("ZZ-" + uniqueCode("S"), "Fisiologia");
        SubjectView earlier = curation.defineSubject("AA-" + uniqueCode("S"), "Anatomia Humana");

        List<Map<String, Object>> body = items(mockMvc.perform(get(SUBJECTS)
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(body).allSatisfy(item -> assertThat(item.keySet())
                .as("exactly the published fields; anything more is a leak")
                .isEqualTo(FIELDS));
        assertThat(body).extracting(item -> item.get("id"))
                .containsExactly(earlier.id().toString(), later.id().toString());
        assertThat(body).extracting(item -> item.get("name"))
                .containsExactly("Anatomia Humana", "Fisiologia");
        assertThat(body).extracting(item -> item.get("position"))
                .as("subjects have no curated order; position is their rank by code, from 1")
                .containsExactly(1, 2);
    }

    @Test
    void topicsComeBackWithExactlyIdNameAndPositionInCurricularOrder() throws Exception {
        SubjectView subject = subject("Anatomia Humana");
        SubjectView other = subject("Fisiologia");
        TopicView third = topicNamed(subject, "cranio", "Osteologia do crânio", 3);
        TopicView first = topicNamed(subject, "terminologia", "Terminologia anatômica", 1);
        TopicView second = topicNamed(subject, "osteologia", "Osteologia geral", 2);
        topicNamed(other, "celula", "Fisiologia celular", 1);

        List<Map<String, Object>> body = items(mockMvc.perform(
                        get(SUBJECTS + "/" + subject.id() + "/topics")
                                .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(body).allSatisfy(item -> assertThat(item.keySet())
                .as("no effort band, no minutes, no edges, no timestamps")
                .isEqualTo(FIELDS));
        assertThat(body).extracting(item -> item.get("id"))
                .as("by position, and only this subject's topics")
                .containsExactly(first.id().toString(), second.id().toString(),
                        third.id().toString());
        assertThat(body).extracting(item -> item.get("name"))
                .containsExactly("Terminologia anatômica", "Osteologia geral",
                        "Osteologia do crânio");
        assertThat(body).extracting(item -> item.get("position")).containsExactly(1, 2, 3);
    }

    @Test
    void aSubjectWithoutTopicsHasAnEmptyList() throws Exception {
        SubjectView subject = subject("Disciplina vazia");

        mockMvc.perform(get(SUBJECTS + "/" + subject.id() + "/topics")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void anUnknownSubjectIsTheExistingNotFoundProblem() throws Exception {
        UUID unknown = UUID.randomUUID();

        MvcResult result = mockMvc.perform(get(SUBJECTS + "/" + unknown + "/topics")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:sinapse:problem:resource-not-found"))
                .andReturn();

        assertThat(result.getResponse().getContentType())
                .startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat((String) problemOf(result).get("detail"))
                .as("detail never carries an internal identifier")
                .doesNotContain(unknown.toString());
    }

    @Test
    void anAnonymousCallerGetsWhatEveryOtherRouteAnswers() throws Exception {
        SubjectView subject = subject("Anatomia Humana");
        MvcResult reference = mockMvc.perform(get("/api/v1/goals")).andReturn();

        for (String route : List.of(SUBJECTS, SUBJECTS + "/" + subject.id() + "/topics")) {
            MvcResult anonymous = mockMvc.perform(get(route))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.type").value("urn:sinapse:problem:unauthenticated"))
                    .andReturn();

            assertThat(anonymous.getResponse().getContentType())
                    .isEqualTo(reference.getResponse().getContentType());
            assertThat(problemOf(anonymous))
                    .as("the same problem body an established route gives, route aside")
                    .usingRecursiveComparison()
                    .ignoringFields("instance")
                    .isEqualTo(problemOf(reference));
        }
    }

    private TopicView topicNamed(SubjectView subject, String code, String name, int position) {
        return curation.defineTopic(new CatalogCuration.TopicDefinition(subject.id(), code, name,
                position, EffortTier.LONG));
    }

    private List<Map<String, Object>> items(MvcResult result) throws Exception {
        return objectMapper.readValue(result.getResponse().getContentAsByteArray(), ITEMS);
    }

    private Map<String, Object> problemOf(MvcResult result) throws Exception {
        return objectMapper.readValue(result.getResponse().getContentAsByteArray(),
                new TypeReference<>() { });
    }
}
