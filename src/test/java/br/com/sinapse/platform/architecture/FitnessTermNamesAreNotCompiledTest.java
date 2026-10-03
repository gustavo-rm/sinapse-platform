package br.com.sinapse.platform.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * The platform does not know the core's fitness terms by name.
 *
 * <p>The core declares which terms it judged a plan by, what they are called and how they are
 * weighted; the platform stores that report and passes it through. A term name compiled into
 * this side would mean a term added to the core needs a deploy here, and a term the core stops
 * reporting could turn into a zero on a screen — a statement the core never made.
 *
 * <p>Checked by scanning {@code src/main} for every term name the test documents know: the
 * top-level keys of the reference response's {@code fitness}, and every {@code name} in the
 * fitness reports under {@code src/test/resources/fitness/}. A literal is a quoted string in any
 * file, or a configuration key in a non-Java one. Prose mentioning a word in a comment is not a
 * literal and is not flagged.
 */
class FitnessTermNamesAreNotCompiledTest {

    private static final Path MAIN = Path.of("src", "main");

    private static final List<String> REPORTS = List.of(
            "fitness/fitness-report-run-a.json",
            "fitness/fitness-report-run-b.json");

    private static final String REFERENCE_RESPONSE = "contract/plan-response-v1.0.json";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void noFitnessTermNameAppearsAsALiteralInMainSources() throws IOException {
        Set<String> terms = knownTermNames();
        assertThat(terms).as("the documents this check reads still name some terms").isNotEmpty();

        List<String> offences = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                boolean java = file.toString().endsWith(".java");
                for (String term : terms) {
                    if (isLiteralIn(content, term, java)) {
                        offences.add(file + " names the fitness term '" + term + "'");
                    }
                }
            }
        }

        assertThat(offences)
                .as("the core declares its fitness terms; the platform renders whatever it "
                        + "reports and must not name any of them")
                .isEmpty();
    }

    private static boolean isLiteralIn(String content, String term, boolean java) {
        String quoted = Pattern.quote(term);
        if (Pattern.compile("[\"']" + quoted + "[\"']").matcher(content).find()) {
            return true;
        }
        return !java && Pattern.compile("(?m)^\\s*" + quoted + "\\s*[:=]").matcher(content).find();
    }

    private Set<String> knownTermNames() throws IOException {
        Set<String> names = new TreeSet<>();
        read(REFERENCE_RESPONSE).get("fitness").fieldNames().forEachRemaining(names::add);
        for (String report : REPORTS) {
            collectNames(read(report), names);
        }
        return names;
    }

    private static void collectNames(JsonNode node, Set<String> names) {
        if (node.isObject()) {
            JsonNode name = node.get("name");
            if (name != null && name.isTextual()) {
                names.add(name.asText());
            }
        }
        for (Iterator<JsonNode> children = node.elements(); children.hasNext(); ) {
            collectNames(children.next(), names);
        }
    }

    private JsonNode read(String path) throws IOException {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return objectMapper.readTree(in);
        }
    }
}
