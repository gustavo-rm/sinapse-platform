package br.com.sinapse.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.sinapse.platform.identity.api.ConsentPurpose;
import br.com.sinapse.platform.identity.internal.domain.Account;
import br.com.sinapse.platform.identity.support.IdentityFixtures;
import br.com.sinapse.platform.planning.support.PlanningIntegrationTest;
import br.com.sinapse.platform.shared.ratelimit.InMemoryRateLimiter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The error schema the specification declares is the body the API actually sends.
 *
 * <p>A generated client types every error response with that schema, so a member the schema
 * leaves out is one the client cannot read without a cast, and a member it declares and the wire
 * never carries is one the client waits for in vain. Both happened with springdoc's own
 * {@code ProblemDetail} schema: it declared a {@code properties} map that Jackson flattens into
 * the top level, and it had no {@code errors} member, which every validation failure sends.
 *
 * <p>So this test sends one request for each kind of error the platform emits — from the
 * dispatcher's exception handler and from the writer the security chain and the rate limiter
 * use outside it — and holds each real body against the schema the error responses reference:
 * no member the schema does not declare, every member it marks as required, the same rule one
 * level down for an array whose items are a schema of their own, and, across all of them, no
 * declared member that no body ever carries.
 *
 * <p>Every case also asserts its status and its {@code type}, so a case that drifted into
 * another error would fail here instead of silently testing the wrong body.
 */
class OpenApiProblemSchemaIntegrationTest extends PlanningIntegrationTest {

    private static final String PROBLEM_JSON = MediaType.APPLICATION_PROBLEM_JSON_VALUE;

    private static final String TYPE_PREFIX = "urn:sinapse:problem:";

    /**
     * Documentation address (RFC 5737) used as the origin of the sign-in attempts, so that
     * exhausting the sign-in limit here counts against nobody else's window.
     */
    private static final String ORIGIN = "198.51.100.23";

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InMemoryRateLimiter limiter;

    @BeforeEach
    @AfterEach
    void clearCounters() {
        limiter.reset();
    }

    @Test
    void everyErrorResponseReferencesTheSameSchema() throws Exception {
        Map<String, List<String>> operationsBySchema = errorSchemaReferences(specification());

        assertThat(operationsBySchema.keySet())
                .as("error responses described with different schemas, or with none")
                .hasSize(1)
                .allMatch(reference -> reference.startsWith("#/components/schemas/"));
    }

    @Test
    void everyErrorBodyCarriesExactlyTheMembersItsSchemaDeclares() throws Exception {
        JsonNode specification = specification();
        Map<String, List<String>> operationsBySchema = errorSchemaReferences(specification);
        assertThat(operationsBySchema).as("one schema for every error response").hasSize(1);
        JsonNode schema = resolve(specification, operationsBySchema.keySet().iterator().next());

        Map<String, List<String>> violations = new TreeMap<>();
        Set<String> sent = new TreeSet<>();
        errorBodies().forEach((name, body) -> {
            body.fieldNames().forEachRemaining(sent::add);
            List<String> found = new ArrayList<>();
            check("", body, schema, specification, found);
            if (!found.isEmpty()) {
                violations.put(name, found);
            }
        });
        List<String> neverSent = new ArrayList<>();
        schema.path("properties").fieldNames().forEachRemaining(member -> {
            if (!sent.contains(member)) {
                neverSent.add("declared member " + member + " is in no body");
            }
        });
        if (!neverSent.isEmpty()) {
            violations.put("every case", neverSent);
        }

        assertThat(violations)
                .as("error bodies that do not match the declared schema")
                .isEmpty();
    }

    /** One real body for each kind of error, by the name of the case that produced it. */
    private Map<String, JsonNode> errorBodies() throws Exception {
        Account student = student();
        String token = "Bearer " + tokenFor(student);
        Account unverified = identity.registerAdult(identity.uniqueEmail());
        String unverifiedToken = "Bearer " + tokenFor(unverified);
        String registered = identity.uniqueEmail();
        identity.registerAdult(registered);

        Map<String, JsonNode> bodies = new LinkedHashMap<>();
        bodies.put("400 validation-failed", body(post("/api/v1/accounts")
                .contentType(MediaType.APPLICATION_JSON).content("{}"),
                400, "validation-failed"));
        bodies.put("400 malformed-request", body(post("/api/v1/accounts")
                .contentType(MediaType.APPLICATION_JSON).content("{\"email\":"),
                400, "malformed-request"));
        bodies.put("400 time-window-invalid", body(get("/api/v1/study-sessions")
                .header(HttpHeaders.AUTHORIZATION, token)
                .param("from", "2026-09-02T00:00:00Z").param("to", "2026-09-01T00:00:00Z"),
                400, "time-window-invalid"));
        bodies.put("401 unauthenticated, security chain", body(get("/api/v1/goals"),
                401, "unauthenticated"));
        bodies.put("401 unauthenticated, exception handler", body(signIn("wrong-password"),
                401, "unauthenticated"));
        bodies.put("403 access-denied, security chain", body(get("/api/v1/classrooms")
                .header(HttpHeaders.AUTHORIZATION, token),
                403, "access-denied"));
        bodies.put("403 access-denied, exception handler", body(get("/api/v1/goals")
                .header(HttpHeaders.AUTHORIZATION, unverifiedToken),
                403, "access-denied"));
        bodies.put("404 resource-not-found, unknown resource", body(
                get("/api/v1/subjects/" + UUID.randomUUID() + "/topics")
                        .header(HttpHeaders.AUTHORIZATION, token),
                404, "resource-not-found"));
        bodies.put("404 resource-not-found, unknown route", body(get("/api/v1/no-such-route")
                .header(HttpHeaders.AUTHORIZATION, token),
                404, "resource-not-found"));
        bodies.put("405 method-not-allowed", body(delete("/api/v1/terms"),
                405, "method-not-allowed"));
        bodies.put("409 conflict", body(post("/api/v1/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registration(registered, identity.adultDateOfBirth())),
                409, "conflict"));
        bodies.put("409 setup-incomplete", body(post("/api/v1/study-plans/generation-requests")
                .header(HttpHeaders.AUTHORIZATION, token),
                409, "setup-incomplete"));
        bodies.put("415 unsupported-media-type", body(post("/api/v1/accounts")
                .contentType(MediaType.TEXT_PLAIN).content("{}"),
                415, "unsupported-media-type"));
        bodies.put("422 minor-registration-not-supported", body(post("/api/v1/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registration(identity.uniqueEmail(), LocalDate.now(clock).minusYears(17))),
                422, "minor-registration-not-supported"));
        bodies.put("429 rate-limit-exceeded", rateLimited());
        bodies.put("500 internal-error", body(get("/api/v1/probe/failure"),
                500, "internal-error"));
        return bodies;
    }

    /** Signs in from {@link #ORIGIN} until the window is exhausted, and keeps the refusal. */
    private JsonNode rateLimited() throws Exception {
        for (int attempt = 0; attempt < 20; attempt++) {
            MockHttpServletResponse response = mockMvc.perform(signIn("wrong-password"))
                    .andReturn().getResponse();
            if (response.getStatus() == 429) {
                assertThat(response.getHeader(HttpHeaders.RETRY_AFTER))
                        .as("a rate limit refusal says when to retry")
                        .isNotBlank();
                return problemOf(response, 429, "rate-limit-exceeded");
            }
        }
        throw new AssertionError("the sign-in window was never exhausted");
    }

    private MockHttpServletRequestBuilder signIn(String password) {
        return post("/api/v1/sessions")
                .with(request -> {
                    request.setRemoteAddr(ORIGIN);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}"
                        .formatted(identity.uniqueEmail(), password));
    }

    private String registration(String email, LocalDate dateOfBirth) {
        return """
                {"email":"%s","password":"%s","dateOfBirth":"%s","timeZone":"%s",
                 "acceptedTerms":[{"purpose":"LEARNING_DATA_PROCESSING","termsVersionId":"%s"}]}
                """.formatted(email, IdentityFixtures.DEFAULT_PASSWORD, dateOfBirth,
                IdentityFixtures.DEFAULT_ZONE,
                identity.currentTermsId(ConsentPurpose.LEARNING_DATA_PROCESSING));
    }

    private JsonNode body(RequestBuilder request, int status, String type) throws Exception {
        return problemOf(mockMvc.perform(request).andReturn().getResponse(), status, type);
    }

    private static JsonNode problemOf(MockHttpServletResponse response, int status, String type)
            throws Exception {
        assertThat(response.getStatus()).as("status of the %s case", type).isEqualTo(status);
        assertThat(MediaType.parseMediaType(response.getContentType())
                        .isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .as("media type of the %s case: %s", type, response.getContentType())
                .isTrue();
        JsonNode body = JSON.readTree(response.getContentAsString(StandardCharsets.UTF_8));
        assertThat(body.path("type").asText()).isEqualTo(TYPE_PREFIX + type);
        return body;
    }

    /**
     * The members of one object against a schema: undeclared members, missing required ones, and
     * the same for the elements of any array whose items reference a schema of their own.
     */
    private static void check(String path, JsonNode body, JsonNode schema, JsonNode specification,
            List<String> found) {
        JsonNode properties = schema.path("properties");
        body.fieldNames().forEachRemaining(member -> {
            if (!properties.has(member)) {
                found.add("undeclared member " + path + member);
            }
        });
        schema.path("required").forEach(required -> {
            if (!body.has(required.asText())) {
                found.add("missing required member " + path + required.asText());
            }
        });
        properties.fields().forEachRemaining(property -> {
            JsonNode items = property.getValue().path("items").path("$ref");
            JsonNode value = body.path(property.getKey());
            if (items.isTextual() && value.isArray()) {
                JsonNode itemSchema = resolve(specification, items.asText());
                for (int index = 0; index < value.size(); index++) {
                    check(path + property.getKey() + "[" + index + "].", value.get(index),
                            itemSchema, specification, found);
                }
            }
        });
    }

    /** Every schema the error responses reference, with the operations that reference it. */
    private static Map<String, List<String>> errorSchemaReferences(JsonNode specification) {
        Map<String, List<String>> operationsBySchema = new TreeMap<>();
        specification.path("paths").fields().forEachRemaining(path ->
                path.getValue().fields().forEachRemaining(operation ->
                        operation.getValue().path("responses").fields().forEachRemaining(response -> {
                            if (!response.getKey().startsWith("4")
                                    && !response.getKey().startsWith("5")) {
                                return;
                            }
                            String reference = response.getValue().path("content")
                                    .path(PROBLEM_JSON).path("schema").path("$ref").asText("none");
                            operationsBySchema.computeIfAbsent(reference, key -> new ArrayList<>())
                                    .add(operation.getKey().toUpperCase() + " " + path.getKey()
                                            + " " + response.getKey());
                        })));
        return operationsBySchema;
    }

    private static JsonNode resolve(JsonNode specification, String reference) {
        JsonNode schema = specification.at(reference.substring(1));
        assertThat(schema.isMissingNode()).as("%s does not resolve", reference).isFalse();
        return schema;
    }

    private JsonNode specification() throws Exception {
        return JSON.readTree(mockMvc.perform(get("/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
