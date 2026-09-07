package br.com.sinapse.platform.curation;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.sinapse.platform.curation.internal.CatalogFiles;
import br.com.sinapse.platform.curation.internal.TextbookOrderSeeder;
import br.com.sinapse.platform.curation.internal.model.DesiredCatalogue;
import br.com.sinapse.platform.curation.internal.model.DesiredEdge;
import br.com.sinapse.platform.curriculum.api.EdgeProvenance;
import br.com.sinapse.platform.curriculum.api.EdgeStrength;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Seeding writes to the file and never to the database.
 *
 * <p>That is the whole point of the command and it is easy to get wrong in the convenient
 * direction: seeding straight into the catalogue would work, and would quietly break the
 * argument the file is there to make. Once the file no longer describes the full desired state,
 * the next import tries to remove everything the seeding added.
 */
class TextbookOrderSeederTest {

    @TempDir
    Path root;

    @Test
    void consecutiveTopicsGainSoftTextbookOrderEdgesInTheFile() {
        write("SUB", """
                code,name,position,effort_tier
                a,A,1,SHORT
                b,B,2,SHORT
                c,C,3,SHORT
                """, """
                prerequisite,dependent,strength,provenance,source_reference
                """);

        TextbookOrderSeeder.Result result = TextbookOrderSeeder.seed(root, "SUB", read());

        assertThat(result.created()).isEqualTo(2);
        assertThat(result.kept()).isZero();
        assertThat(read().edges())
                .extracting(DesiredEdge::strength, DesiredEdge::provenance)
                .containsOnly(org.assertj.core.groups.Tuple.tuple(
                        EdgeStrength.SOFT, EdgeProvenance.TEXTBOOK_ORDER));
        assertThat(read().edges()).extracting(edge -> edge.prerequisite() + " -> " + edge.dependent())
                .containsExactly("SUB:a -> SUB:b", "SUB:b -> SUB:c");
    }

    /** The definition of done: a second run changes nothing, including the bytes of the file. */
    @Test
    void runningItTwiceProducesNoChangeAtAll() {
        write("SUB", """
                code,name,position,effort_tier
                a,A,1,SHORT
                b,B,2,SHORT
                c,C,3,SHORT
                """, """
                prerequisite,dependent,strength,provenance,source_reference
                """);
        TextbookOrderSeeder.seed(root, "SUB", read());
        String afterFirst = read(root.resolve("SUB").resolve("prerequisites.csv"));

        TextbookOrderSeeder.Result second = TextbookOrderSeeder.seed(root, "SUB", read());

        assertThat(second.created()).isZero();
        assertThat(second.kept()).isEqualTo(2);
        assertThat(read(root.resolve("SUB").resolve("prerequisites.csv"))).isEqualTo(afterFirst);
    }

    /** A human's judgement is never overwritten by the order a book happened to print in. */
    @Test
    void aCuratedEdgeOnTheSamePairIsLeftExactlyAsItIs() {
        write("SUB", """
                code,name,position,effort_tier
                a,A,1,SHORT
                b,B,2,SHORT
                c,C,3,SHORT
                """, """
                prerequisite,dependent,strength,provenance,source_reference
                SUB:a,SUB:b,HARD,CURATED,Moore cap. 1
                """);

        TextbookOrderSeeder.Result result = TextbookOrderSeeder.seed(root, "SUB", read());

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.kept()).isEqualTo(1);
        assertThat(read().edges())
                .filteredOn(edge -> edge.dependent().topicCode().equals("b"))
                .singleElement()
                .satisfies(edge -> {
                    assertThat(edge.strength()).isEqualTo(EdgeStrength.HARD);
                    assertThat(edge.provenance()).isEqualTo(EdgeProvenance.CURATED);
                    assertThat(edge.sourceReference()).isEqualTo("Moore cap. 1");
                });
    }

    /** Order comes from the declared position, not from the order of the rows in the file. */
    @Test
    void theEdgesFollowTheCurricularPositionAndNotTheRowOrder() {
        write("SUB", """
                code,name,position,effort_tier
                c,C,3,SHORT
                a,A,1,SHORT
                b,B,2,SHORT
                """, """
                prerequisite,dependent,strength,provenance,source_reference
                """);

        TextbookOrderSeeder.seed(root, "SUB", read());

        assertThat(read().edges()).extracting(edge -> edge.prerequisite() + " -> " + edge.dependent())
                .containsExactly("SUB:a -> SUB:b", "SUB:b -> SUB:c");
    }

    @Test
    void aSubjectWithOneTopicHasNothingToSeedAndIsNotAFailure() {
        write("SUB", """
                code,name,position,effort_tier
                a,A,1,SHORT
                """, """
                prerequisite,dependent,strength,provenance,source_reference
                """);

        assertThat(TextbookOrderSeeder.seed(root, "SUB", read()).created()).isZero();
    }

    private DesiredCatalogue read() {
        return CatalogFiles.read(root, null);
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private void write(String code, String topics, String prerequisites) {
        try {
            Path directory = Files.createDirectories(root.resolve(code));
            Files.writeString(directory.resolve("topics.csv"), topics, StandardCharsets.UTF_8);
            Files.writeString(directory.resolve("prerequisites.csv"), prerequisites,
                    StandardCharsets.UTF_8);
        } catch (IOException unwritable) {
            throw new UncheckedIOException(unwritable);
        }
    }
}
