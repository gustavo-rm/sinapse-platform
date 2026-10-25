package br.com.sinapse.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Every operation of the generated specification has a stable, unique name.
 *
 * <p>A generated client names its methods after the {@code operationId}, so the name is part of
 * the API. springdoc takes it from the Java method name and, when two handlers share one,
 * appends {@code _1}, {@code _2}… in the order it discovers the controllers: a new handler
 * called {@code list} can renumber the existing ones and break every client generated before
 * it. Hence the rule this test holds: each name is unique, carries no numeric suffix and is a
 * plain lowerCamelCase identifier. A handler whose method name collides with another one has to
 * say its own name with {@code @Operation(operationId = ...)}.
 *
 * <p>Nor may a name be an ECMAScript or TypeScript reserved word: the Angular generator cannot
 * name a method {@code export} and emits {@code _export} instead, a name nobody chose. What the
 * test cannot catch is a name that is legal but vague; that stays a matter of review.
 *
 * <p>Separate from {@link OpenApiSnapshotIntegrationTest} on purpose. The snapshot says the
 * document did not change unnoticed; this says the names are the kind that do not change on
 * their own. Regenerating the snapshot after a renumbering would satisfy the first and not this
 * one.
 *
 * <p>The document is read from the test profile's context, so the test-only probe routes are
 * held to the same rule as the production ones.
 */
class OpenApiOperationIdIntegrationTest extends IntegrationTest {

    /** What springdoc appends to a name it has already handed out. */
    private static final Pattern DEDUPLICATION_SUFFIX = Pattern.compile("_\\d+$");

    /** A lowerCamelCase identifier, which every client generator turns into a method name. */
    private static final Pattern LOWER_CAMEL_CASE = Pattern.compile("^[a-z][A-Za-z0-9]*$");

    /**
     * Reserved words of ECMAScript and TypeScript, which a TypeScript client generator cannot use
     * as a method name as they are.
     */
    private static final Set<String> RESERVED_WORDS = Set.of(
            "break", "case", "catch", "class", "const", "continue", "debugger", "default",
            "delete", "do", "else", "enum", "export", "extends", "false", "finally", "for",
            "function", "if", "implements", "import", "in", "instanceof", "interface", "let",
            "new", "null", "package", "private", "protected", "public", "return", "static",
            "super", "switch", "this", "throw", "true", "try", "typeof", "var", "void", "while",
            "with", "yield", "await");

    private static final Set<String> HTTP_METHODS =
            Set.of("get", "put", "post", "delete", "options", "head", "patch", "trace");

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Test
    void everyOperationHasAUniqueStableName() throws Exception {
        Map<String, List<String>> operationsById = new TreeMap<>();
        List<String> unnamed = new ArrayList<>();
        specification().get("paths").fields().forEachRemaining(path ->
                path.getValue().fields().forEachRemaining(operation -> {
                    if (!HTTP_METHODS.contains(operation.getKey())) {
                        return;
                    }
                    String where = operation.getKey().toUpperCase() + " " + path.getKey();
                    JsonNode id = operation.getValue().get("operationId");
                    if (id == null || id.asText().isBlank()) {
                        unnamed.add(where);
                    } else {
                        operationsById.computeIfAbsent(id.asText(), name -> new ArrayList<>())
                                .add(where);
                    }
                }));

        assertThat(operationsById).as("the specification describes no operation").isNotEmpty();
        assertThat(unnamed).as("operations without an operationId").isEmpty();
        assertThat(offenders(operationsById, id -> operationsById.get(id).size() > 1))
                .as("operationIds shared by more than one operation")
                .isEmpty();
        assertThat(offenders(operationsById, id -> DEDUPLICATION_SUFFIX.matcher(id).find()))
                .as("operationIds carrying springdoc's numeric suffix, assigned in discovery order: "
                        + "give each handler its own name with @Operation(operationId = ...)")
                .isEmpty();
        assertThat(offenders(operationsById, id -> !LOWER_CAMEL_CASE.matcher(id).matches()))
                .as("operationIds that are not lowerCamelCase identifiers")
                .isEmpty();
        assertThat(offenders(operationsById, RESERVED_WORDS::contains))
                .as("operationIds that are ECMAScript or TypeScript reserved words, which a "
                        + "generated client has to rename")
                .isEmpty();
    }

    /** The names a rule rejects, each with the operations that carry it. */
    private static Map<String, List<String>> offenders(Map<String, List<String>> operationsById,
            Predicate<String> rejectsName) {
        Map<String, List<String>> rejected = new TreeMap<>(operationsById);
        rejected.keySet().removeIf(rejectsName.negate());
        return rejected;
    }

    private JsonNode specification() throws Exception {
        return JSON.readTree(mockMvc.perform(get("/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
