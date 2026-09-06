package br.com.sinapse.platform.readmodel.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.sinapse.platform.readmodel.support.ReadModelIntegrationTest;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The generated specification describes the routes section 3 of the API contract specifies.
 *
 * <p>The definition of done for these read models asks for the specification to match that
 * document, and springdoc generates it from the annotations on the controllers — so the way to
 * check the annotations are there is to read what came out, not to read the source. A route
 * with no {@code @Operation} still works and still appears; what it loses is the description a
 * client integrates against, and nothing else in the suite would notice.
 *
 * <p>The window parameters are asserted as required because that is the half of section 1 of the
 * contract a client cannot discover by trial: this API has no generic pagination, so a history
 * read that omits them is refused rather than defaulted.
 */
class ReadModelOpenApiIntegrationTest extends ReadModelIntegrationTest {

    /** The five paths this component publishes, as the contract spells them. */
    private static final List<String> PATHS = List.of(
            "/api/v1/me/state",
            "/api/v1/me/agenda",
            "/api/v1/study-plans/{planId}/summary",
            "/api/v1/classrooms/{classroomId}/students",
            "/api/v1/classrooms/{classroomId}/students/{accountId}/panel");

    /** The three that take a mandatory window. */
    private static final List<String> WINDOWED = List.of(
            "/api/v1/me/agenda",
            "/api/v1/classrooms/{classroomId}/students",
            "/api/v1/classrooms/{classroomId}/students/{accountId}/panel");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void everyReadModelRouteIsDescribedInTheGeneratedSpecification() throws Exception {
        DocumentContext specification = specification();

        for (String path : PATHS) {
            assertThat(specification.<Map<String, Object>>read("$.paths['" + path + "'].get"))
                    .as("no operation described for %s", path)
                    .isNotNull();
            assertThat(specification.<String>read("$.paths['" + path + "'].get.summary"))
                    .as("no summary on %s", path)
                    .isNotBlank();
            assertThat(specification.<Map<String, Object>>read(
                            "$.paths['" + path + "'].get.responses['200']"))
                    .as("no success response described for %s", path)
                    .isNotNull();
        }
    }

    @Test
    void theMandatoryWindowIsPartOfTheDescribedContract() throws Exception {
        DocumentContext specification = specification();

        for (String path : WINDOWED) {
            List<String> required = specification.read(
                    "$.paths['" + path + "'].get.parameters[?(@.required == true)].name");
            assertThat(required)
                    .as("the window of %s is mandatory and the specification has to say so", path)
                    .contains("from", "to");
        }
    }

    /** The error contract of ADR 0009 is described, not just implemented. */
    @Test
    void theProblemResponsesAreDescribedAsProblemJson() throws Exception {
        DocumentContext specification = specification();

        Map<String, Object> content = specification.read(
                "$.paths['/api/v1/me/agenda'].get.responses['400'].content");
        assertThat(content).containsKey("application/problem+json");
    }

    private DocumentContext specification() throws Exception {
        String body = mockMvc.perform(get("/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.parse(body);
    }
}
