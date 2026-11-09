package br.com.sinapse.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * With no profile active, as a deployment runs, the API documentation is not served (ADR 0018).
 *
 * <p>The specification and the Swagger interface describe every route, parameter and error of the
 * platform. The security chain leaves them open, through the fallback that permits whatever no
 * module claimed, so the only thing that keeps them from an anonymous caller is that the base
 * configuration does not switch them on. Only the local and test profiles do.
 *
 * <p>An anonymous request to each documentation path must therefore end in a client error, not
 * in the document, its configuration or the interface, and not in a redirect to the interface.
 * The body is checked as well as the status: a status alone would not notice a path that
 * started answering the specification under another code.
 *
 * <p>The context is the one a deployment starts: no profile, scheduled jobs off so that they do
 * not take jobs other test classes leave in the shared database, and the pool capped as the test
 * profile caps it. It is closed after the class, like the snapshot test's, so that it does not
 * hold connections while the rest of the suite runs.
 */
@ActiveProfiles(profiles = {}, inheritProfiles = false)
@TestPropertySource(properties = {
        "sinapse.planning.generation.poll-cron=-",
        "sinapse.data-rights.sweep-cron=-",
        "sinapse.learning-record.consistency-check-cron=-",
        "sinapse.identity.majority-sweep-cron=-",
        "spring.datasource.hikari.maximum-pool-size=5"})
@DirtiesContext
class ApiDocumentationClosedByDefaultIntegrationTest extends IntegrationTest {

    /**
     * What the document, its configuration and the interface carry, and no error body does: the
     * version key of the specification, the title of the API, the key of the interface
     * configuration and the page of the interface.
     */
    private static final String[] DOCUMENTATION_MARKERS = {
            "\"openapi\"", "Sinapse Platform API", "\"configUrl\"", "Swagger UI", "<html"};

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest
    @ValueSource(strings = {"/api-docs", "/api-docs/swagger-config", "/swagger-ui/index.html",
            "/swagger-ui.html"})
    void anAnonymousCallerGetsNoDocumentationWithoutAProfile(String path) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(get(path)).andReturn().getResponse();

        assertThat(HttpStatus.valueOf(response.getStatus()).is4xxClientError())
                .as("%s answered %d; with no profile it must not be served", path,
                        response.getStatus())
                .isTrue();
        assertThat(response.getContentAsString(StandardCharsets.UTF_8))
                .as("body of %s", path)
                .doesNotContain(DOCUMENTATION_MARKERS);
    }
}
