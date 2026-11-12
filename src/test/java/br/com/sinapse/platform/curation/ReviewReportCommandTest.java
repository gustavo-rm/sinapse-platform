package br.com.sinapse.platform.curation;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.sinapse.platform.curation.internal.CatalogCommand;
import br.com.sinapse.platform.curation.internal.CatalogRunner;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code catalog report}: what a reviewer reads instead of the CSV.
 *
 * <p>Files only, so no database: the runner is built without the two collaborators that need one.
 * Synthetic data throughout.
 */
class ReviewReportCommandTest {

    @TempDir
    Path root;

    private final List<String> printed = new ArrayList<>();

    /**
     * {@code SYN-A}: a chain T01 -> T02 -> T03, T03 leading into {@code SYN-B}, and T04 alone.
     * {@code SYN-B}: T01 -> T02, reviewed, with one {@code CURATED} edge.
     */
    @BeforeEach
    void twoSubjects() {
        write("SYN-A", "topics.csv", """
                code,name,position,effort_tier
                T01,Topic 01,1,SHORT
                T02,Topic 02,2,STANDARD
                T03,Topic 03,3,STANDARD
                T04,Topic 04,4,EXTENDED
                """);
        write("SYN-A", "prerequisites.csv", """
                prerequisite,dependent,strength,provenance,source_reference
                SYN-A:T01,SYN-A:T02,HARD,PROPOSED,Synthetic source 1
                SYN-A:T02,SYN-A:T03,SOFT,PROPOSED,
                SYN-A:T03,SYN-B:T01,HARD,PROPOSED,
                """);
        write("SYN-B", "topics.csv", """
                code,name,position,effort_tier
                T01,Topic 01,1,LONG
                T02,Topic 02,2,LONG
                """);
        write("SYN-B", "prerequisites.csv", """
                prerequisite,dependent,strength,provenance,source_reference
                SYN-B:T01,SYN-B:T02,HARD,CURATED,Synthetic source 2
                """);
        write("SYN-B", "reviews.csv", "reviewer,reviewed_on\nREV-01,2026-11-01\n");
    }

    @Test
    void theReportShowsTheShapeOfEachSubjectsGraph() {
        assertThat(run(null)).isZero();
        String report = String.join("\n", printed);

        assertThat(report).contains(
                "catalog report  (2 subject(s), from the files)",
                "  SYN-A  SYN-A",
                "    4 topic(s), 3 edge(s) declared in SYN-A/prerequisites.csv",
                "    edges citing no source: 2",
                "    review: none recorded in SYN-A/reviews.csv",
                "    edges across subjects: 1 leaving, 0 arriving",
                "      SYN-A:T03 -> SYN-B:T01  (HARD, PROPOSED)",
                "    depth: the longest prerequisite chain inside the subject has 3 topic(s): T01 -> T02 -> T03",
                "    entry topics, with no prerequisite at all: 2",
                "    leaf topics, that nothing depends on: 1",
                "    effort bands: SHORT 1 STANDARD 2 LONG 0 EXTENDED 1",
                "  SYN-B  SYN-B",
                "    review: 2026-11-01 by REV-01",
                "    edges across subjects: 0 leaving, 1 arriving",
                "    depth: the longest prerequisite chain inside the subject has 2 topic(s): T01 -> T02",
                "    entry topics, with no prerequisite at all: 0",
                "  all subjects read: 6 topic(s), 4 edge(s), 1 across subjects",
                "    longest prerequisite chain overall: 5 topic(s): SYN-A:T01 -> SYN-A:T02 -> SYN-A:T03"
                        + " -> SYN-B:T01 -> SYN-B:T02");
        assertThat(lineAfter("    entry topics, with no prerequisite at all: 2"))
                .contains("1  T01  Topic 01");
        assertThat(lineAfter("    leaf topics, that nothing depends on: 1")).contains("4  T04  Topic 04");
        assertThat(report).as("the isolated topic is pointed out too").contains("1 warning(s)",
                "topic T04 has no prerequisite and nothing depends on it");
    }

    @Test
    void theStrengthByProvenanceTableCountsEachCell() {
        run("SYN-B");

        assertThat(printed).anySatisfy(line -> assertThat(line.trim())
                .matches("HARD\\s+1\\s+0\\s+0\\s+0\\s+1"));
        assertThat(printed).anySatisfy(line -> assertThat(line.trim())
                .matches("SOFT\\s+0\\s+0\\s+0\\s+0\\s+0"));
        assertThat(printed).anySatisfy(line -> assertThat(line).contains("CURATED", "PROPOSED",
                "TEXTBOOK_ORDER", "DERIVED", "total"));
    }

    @Test
    void edgesStillProposedAfterAReviewAreCounted() {
        write("SYN-B", "prerequisites.csv", """
                prerequisite,dependent,strength,provenance,source_reference
                SYN-B:T01,SYN-B:T02,HARD,PROPOSED,
                """);

        run("SYN-B");

        assertThat(printed).contains("    still PROPOSED after the review: 1 edge(s)");
    }

    @Test
    void aCatalogueThatDoesNotValidateIsNotReportedOn() {
        write("SYN-B", "prerequisites.csv", """
                prerequisite,dependent,strength,provenance,source_reference
                SYN-B:T01,SYN-B:T02,HARD,PROPOSED,
                SYN-B:T02,SYN-B:T01,HARD,PROPOSED,
                """);

        assertThat(run("SYN-B")).isEqualTo(1);
        assertThat(printed.getFirst()).isEqualTo("catalog report  refused: the files do not validate");
        assertThat(String.join("\n", printed)).contains("cycle detected").doesNotContain("depth:");
    }

    private int run(String subject) {
        return new CatalogRunner(null, null, root, printed::add)
                .run(new CatalogCommand(CatalogCommand.Verb.REPORT, subject, false));
    }

    private String lineAfter(String line) {
        return printed.get(printed.indexOf(line) + 1);
    }

    private void write(String subject, String file, String content) {
        try {
            Files.createDirectories(root.resolve(subject));
            Files.writeString(root.resolve(subject).resolve(file), content, StandardCharsets.UTF_8);
        } catch (IOException unwritable) {
            throw new UncheckedIOException(unwritable);
        }
    }
}
