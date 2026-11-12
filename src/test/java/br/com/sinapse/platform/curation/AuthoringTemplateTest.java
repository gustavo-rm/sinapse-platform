package br.com.sinapse.platform.curation;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.sinapse.platform.curation.internal.CatalogFiles;
import br.com.sinapse.platform.curriculum.api.EdgeProvenance;
import br.com.sinapse.platform.curriculum.api.EdgeStrength;
import br.com.sinapse.platform.curriculum.api.EffortTier;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The empty authoring template under {@code docs/curadoria/modelo-de-autoria/}.
 *
 * <p>It is what a curator starts from, so it has to match what the importer reads: one file per
 * file the importer knows, each with exactly its columns, and instructions that name every value
 * the importer accepts. And it has to stay empty — a template with example rows is a template
 * whose example rows end up in somebody's catalogue.
 */
class AuthoringTemplateTest {

    private static final Path TEMPLATE = Path.of("docs/curadoria/modelo-de-autoria");

    @Test
    void theTemplateHasExactlyTheFilesAndColumnsTheImporterReads() throws IOException {
        List<String> files;
        try (Stream<Path> listed = Files.list(TEMPLATE)) {
            files = listed.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".csv"))
                    .sorted()
                    .toList();
        }
        assertThat(files).containsExactlyInAnyOrderElementsOf(CatalogFiles.COLUMNS_BY_FILE.keySet());

        CatalogFiles.COLUMNS_BY_FILE.forEach((file, columns) -> {
            List<String> lines = lines(TEMPLATE.resolve(file));
            assertThat(lines).as(file + " holds its header and nothing else").hasSize(1);
            assertThat(Arrays.asList(lines.getFirst().split(","))).as(file).isEqualTo(columns);
        });
    }

    @Test
    void theInstructionsNameEveryColumnAndEveryAcceptedValue() {
        String instructions = String.join("\n", lines(TEMPLATE.resolve("INSTRUCOES.md")));

        CatalogFiles.COLUMNS_BY_FILE.forEach((file, columns) -> {
            assertThat(instructions).contains("`" + file + "`");
            columns.forEach(column -> assertThat(instructions).contains("`" + column + "`"));
        });
        Stream.of(EffortTier.values(), EdgeStrength.values(), EdgeProvenance.values())
                .flatMap(Arrays::stream)
                .forEach(value -> assertThat(instructions).contains("`" + value.name() + "`"));
    }

    private static List<String> lines(Path file) {
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new java.io.UncheckedIOException(unreadable);
        }
    }
}
