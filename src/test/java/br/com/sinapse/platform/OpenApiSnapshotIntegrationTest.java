package br.com.sinapse.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.util.Separators;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The published API, as the application generates it, against the snapshot committed in
 * {@code docs/openapi/openapi.json}.
 *
 * <p>The snapshot is the contract the frontend generates its client from, and its diff in a pull
 * request is the review of a contract change. So a route, a parameter or a schema that changes
 * without the snapshot being regenerated fails the build here, and the failure says how to
 * regenerate.
 *
 * <p><strong>The production route set.</strong> The context runs with no profile active, as a
 * deployment does: the test profile adds {@code /api/v1/probe/**}, which production does not
 * have, and a snapshot taken under it would describe routes nobody can call. Only the scheduled
 * jobs are switched off, so that a worker in this context does not take jobs that other test
 * classes put in the shared database; no schedule appears in the document. The connection pool
 * is capped at five, here and in the second start, as the test profile caps it for every other
 * context: this one does not run under that profile, and the suite's cached contexts at the
 * default of ten each would exhaust PostgreSQL's default {@code max_connections}. Nothing in the
 * document depends on the pool.
 *
 * <p><strong>The document is switched on here, not by a profile.</strong> The base configuration
 * does not publish {@code /api-docs} (ADR 0018); only the local and test profiles do. Switching
 * to either profile would change the route set the snapshot describes, so this test, and the
 * second start, turn on {@code springdoc.api-docs.enabled} as a property and nothing else. The
 * property decides whether the document is served, not what it says.
 *
 * <p><strong>Stable form.</strong> Object keys sorted at every level, two-space indentation,
 * {@code "key": value}, {@code \n} line ends, one trailing newline. Array order is kept as
 * generated: it carries meaning (parameter order, for one) and two starts produce the same
 * order, which {@link #twoStartsOfTheApplicationGenerateTheSameDocument} checks.
 *
 * <p><strong>What is normalised, and why.</strong> Two fields vary between runs for reasons that
 * have nothing to do with the contract:
 * <ul>
 *   <li>{@code servers}, which springdoc derives from the request that fetched the document:
 *       the host and port of whoever asked;</li>
 *   <li>{@code info.version}, the Maven version of the build ({@code sinapse.api.version}),
 *       which changes on every release without any route changing.</li>
 * </ul>
 * The first is removed, the second replaced by {@value #NORMALISED_VERSION}.
 *
 * <p><strong>Regeneration is explicit.</strong> With {@code -D}{@value #REGENERATE_PROPERTY}
 * {@code =true} the test writes the file instead of comparing; without it, it never writes
 * anywhere.
 */
@ActiveProfiles(profiles = {}, inheritProfiles = false)
@TestPropertySource(properties = {
        "sinapse.planning.generation.poll-cron=-",
        "sinapse.data-rights.sweep-cron=-",
        "sinapse.learning-record.consistency-check-cron=-",
        "sinapse.identity.majority-sweep-cron=-",
        "spring.datasource.hikari.maximum-pool-size=5",
        "springdoc.api-docs.enabled=true"})
@DirtiesContext
class OpenApiSnapshotIntegrationTest extends IntegrationTest {

    /** System property that switches the test from comparing to writing the snapshot. */
    static final String REGENERATE_PROPERTY = "sinapse.openapi.regenerate";

    /** What {@code info.version} is written as, whatever the build version is. */
    static final String NORMALISED_VERSION = "normalised";

    /** The committed snapshot, relative to the module root the build runs in. */
    static final Path SNAPSHOT = Path.of("docs", "openapi", "openapi.json");

    private static final String API_DOCS = "/api-docs";

    private static final String HOW_TO_REGENERATE = "The generated OpenAPI document differs from "
            + SNAPSHOT + "; if the API change is intended, regenerate it with "
            + "./mvnw test -Dtest=OpenApiSnapshotIntegrationTest -D" + REGENERATE_PROPERTY
            + "=true and review the diff of the snapshot in the pull request.";

    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Environment environment;

    @Test
    void theGeneratedDocumentIsTheCommittedSnapshot() throws Exception {
        String generated = stable(fetchFromThisContext());

        if (Boolean.getBoolean(REGENERATE_PROPERTY)) {
            Files.createDirectories(SNAPSHOT.getParent());
            Files.writeString(SNAPSHOT, generated, StandardCharsets.UTF_8);
            return;
        }

        assertThat(SNAPSHOT).as(HOW_TO_REGENERATE).exists();
        assertThat(Files.readString(SNAPSHOT, StandardCharsets.UTF_8))
                .as(HOW_TO_REGENERATE)
                .isEqualTo(generated);
    }

    /**
     * A second, independent start of the whole application, over real HTTP, has to produce the
     * same document byte for byte once normalised. If it does not, the snapshot would fail at
     * random and mean nothing; the message names what moved.
     */
    @Test
    void twoStartsOfTheApplicationGenerateTheSameDocument() throws Exception {
        JsonNode first = JSON.readTree(fetchFromThisContext());
        JsonNode second;
        // Command-line arguments, not default properties: the base configuration has its own
        // datasource and ports, and default properties rank below it.
        try (ConfigurableApplicationContext other = new SpringApplicationBuilder(
                PlatformApplication.class).run(
                        "--spring.datasource.url=" + environment.getProperty("spring.datasource.url"),
                        "--spring.datasource.username="
                                + environment.getProperty("spring.datasource.username"),
                        "--spring.datasource.password="
                                + environment.getProperty("spring.datasource.password"),
                        "--server.port=0",
                        "--management.server.port=0",
                        "--sinapse.planning.generation.poll-cron=-",
                        "--sinapse.data-rights.sweep-cron=-",
                        "--sinapse.learning-record.consistency-check-cron=-",
                        "--sinapse.identity.majority-sweep-cron=-",
                        "--spring.datasource.hikari.maximum-pool-size=5",
                        "--springdoc.api-docs.enabled=true",
                        "--spring.main.banner-mode=off")) {
            String port = other.getEnvironment().getProperty("local.server.port");
            HttpResponse<String> response = HttpClient.newHttpClient().send(HttpRequest
                            .newBuilder(URI.create("http://127.0.0.1:" + port + API_DOCS)).GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            assertThat(response.statusCode()).isEqualTo(200);
            second = JSON.readTree(response.body());
        }

        List<String> rawDifferences = new ArrayList<>();
        differences("$", first, second, rawDifferences);
        assertThat(rawDifferences)
                .as("before normalisation, only the fields the snapshot normalises may differ")
                .allMatch(path -> path.equals("$.servers") || path.startsWith("$.servers["));
        assertThat(stable(JSON.writeValueAsString(second)))
                .as("two starts, the same document")
                .isEqualTo(stable(JSON.writeValueAsString(first)));
    }

    private String fetchFromThisContext() throws Exception {
        return mockMvc.perform(get(API_DOCS))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    /** The document in its committed form: normalised, keys sorted, fixed layout. */
    static String stable(String document) throws IOException {
        ObjectNode tree = (ObjectNode) JSON.readTree(document);
        tree.remove("servers");
        ((ObjectNode) tree.get("info")).put("version", NORMALISED_VERSION);

        Object plain = JSON.treeToValue(tree, Object.class);
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter()
                .withSeparators(Separators.createDefaultInstance()
                        .withObjectFieldValueSpacing(Separators.Spacing.AFTER))
                .withObjectIndenter(new DefaultIndenter("  ", "\n"))
                .withArrayIndenter(new DefaultIndenter("  ", "\n"));
        String written = JSON.writer(printer)
                .without(JsonGenerator.Feature.ESCAPE_NON_ASCII)
                .writeValueAsString(plain);
        return written + "\n";
    }

    private static void differences(String path, JsonNode left, JsonNode right,
            List<String> found) {
        if (left == null || right == null || left.getNodeType() != right.getNodeType()) {
            found.add(path);
            return;
        }
        if (left.isObject()) {
            List<String> names = new ArrayList<>();
            left.fieldNames().forEachRemaining(names::add);
            right.fieldNames().forEachRemaining(name -> {
                if (!names.contains(name)) {
                    names.add(name);
                }
            });
            for (String name : names) {
                differences(path + "." + name, left.get(name), right.get(name), found);
            }
        } else if (left.isArray()) {
            if (left.size() != right.size()) {
                found.add(path);
                return;
            }
            for (int index = 0; index < left.size(); index++) {
                differences(path + "[" + index + "]", left.get(index), right.get(index), found);
            }
        } else if (!left.equals(right)) {
            found.add(path);
        }
    }
}
