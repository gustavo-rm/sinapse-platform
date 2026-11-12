package br.com.sinapse.platform.curation;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.sinapse.platform.curation.internal.CatalogFiles;
import br.com.sinapse.platform.curation.internal.CatalogValidation;
import br.com.sinapse.platform.curation.internal.model.CatalogProblem;
import br.com.sinapse.platform.curation.internal.model.DesiredCatalogue;
import br.com.sinapse.platform.curriculum.api.EdgeProvenance;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The rules that make a catalogue of several subjects curatable with trustworthy provenance.
 *
 * <p>Every rule has a case that fails and a case that passes. The data is synthetic and looks it:
 * subjects {@code SYN-A}, {@code SYN-B}, topics {@code T01}, {@code T02}. Nothing here may look
 * like a curriculum, because nothing here was curated.
 */
class CatalogCurationRulesTest {

    private static final String THREE_TOPICS = """
            code,name,position,effort_tier
            T01,Topic 01,1,SHORT
            T02,Topic 02,2,STANDARD
            T03,Topic 03,3,LONG
            """;

    @TempDir
    Path root;

    // ---------------------------------------------------------------- CURATED needs a source

    @Test
    void aCuratedEdgeWithoutASourceIsRefusedOnItsLine() {
        subject("SYN-A", THREE_TOPICS, """
                prerequisite,dependent,strength,provenance,source_reference
                SYN-A:T01,SYN-A:T02,HARD,CURATED,
                SYN-A:T02,SYN-A:T03,HARD,CURATED,Synthetic source 1
                """);
        reviews("SYN-A", "reviewer,reviewed_on\nREV-01,2026-11-01\n");

        List<CatalogProblem> problems = validate();

        assertThat(problems).singleElement().satisfies(problem -> {
            assertThat(problem.file()).isEqualTo("SYN-A/prerequisites.csv");
            assertThat(problem.line()).isEqualTo(2);
            assertThat(problem.message()).contains("is CURATED and cites no source",
                    "or write PROPOSED");
        });
    }

    @Test
    void aCuratedEdgeWithASourceInAReviewedSubjectPasses() {
        subject("SYN-A", THREE_TOPICS, """
                prerequisite,dependent,strength,provenance,source_reference
                SYN-A:T01,SYN-A:T02,HARD,CURATED,Synthetic source 1
                SYN-A:T02,SYN-A:T03,SOFT,CURATED,Synthetic source 2
                """);
        reviews("SYN-A", "reviewer,reviewed_on\nREV-01,2026-11-01\n");

        assertThat(validate()).isEmpty();
        assertThat(read().reviewsOf("SYN-A")).singleElement()
                .satisfies(review -> assertThat(review.reviewer()).isEqualTo("REV-01"));
    }

    // ---------------------------------------------------------------- CURATED needs a review

    @Test
    void aCuratedEdgeInASubjectNobodyReviewedIsRefusedEvenWithASource() {
        subject("SYN-A", THREE_TOPICS, """
                prerequisite,dependent,strength,provenance,source_reference
                SYN-A:T01,SYN-A:T02,HARD,CURATED,Synthetic source 1
                SYN-A:T02,SYN-A:T03,HARD,PROPOSED,
                """);

        List<CatalogProblem> problems = validate();

        assertThat(problems).singleElement().satisfies(problem -> {
            assertThat(problem.line()).isEqualTo(2);
            assertThat(problem.message()).contains("has no recorded review",
                    "SYN-A/reviews.csv", "or write PROPOSED");
        });
    }

    @Test
    void aProposedEdgeNeedsNeitherASourceNorAReview() {
        subject("SYN-A", THREE_TOPICS, """
                prerequisite,dependent,strength,provenance,source_reference
                SYN-A:T01,SYN-A:T02,HARD,PROPOSED,
                SYN-A:T02,SYN-A:T03,SOFT,PROPOSED,
                """);

        assertThat(validate()).isEmpty();
        assertThat(read().edges()).extracting(edge -> edge.provenance())
                .containsOnly(EdgeProvenance.PROPOSED);
    }

    /**
     * The review that counts is the one of the file the edge is written in: the reviewer signed
     * that file, not the other subject's.
     */
    @Test
    void anotherSubjectsReviewDoesNotCoverAnEdgeWrittenInThisSubjectsFile() {
        subject("SYN-A", THREE_TOPICS, "prerequisite,dependent,strength,provenance,source_reference\n");
        reviews("SYN-A", "reviewer,reviewed_on\nREV-01,2026-11-01\n");
        subject("SYN-B", THREE_TOPICS, """
                prerequisite,dependent,strength,provenance,source_reference
                SYN-A:T03,SYN-B:T01,HARD,CURATED,Synthetic source 1
                """);

        assertThat(validate()).singleElement().satisfies(problem -> {
            assertThat(problem.file()).isEqualTo("SYN-B/prerequisites.csv");
            assertThat(problem.message()).contains("SYN-B has no recorded review");
        });
    }

    @Test
    void anUnreadableReviewIsReportedWithItsLineAndWhatToWrite() {
        subject("SYN-A", THREE_TOPICS, "prerequisite,dependent,strength,provenance,source_reference\n");
        reviews("SYN-A", """
                reviewer,reviewed_on
                ,2026-11-01
                REV-02,01/11/2026
                REV-03,2026-11-03
                """);

        List<CatalogProblem> problems = validate();

        assertThat(problems).extracting(CatalogProblem::file).containsOnly("SYN-A/reviews.csv");
        assertThat(problems).extracting(CatalogProblem::line).containsExactly(2, 3);
        assertThat(problems.get(0).message()).contains("reviewer is blank");
        assertThat(problems.get(1).message()).contains("year-month-day", "2026-11-30");
        assertThat(read().reviewsOf("SYN-A")).singleElement()
                .satisfies(review -> assertThat(review.reviewer()).isEqualTo("REV-03"));
    }

    @Test
    void aReviewFileWithoutItsColumnsIsReportedOnceForTheFile() {
        subject("SYN-A", THREE_TOPICS, "prerequisite,dependent,strength,provenance,source_reference\n");
        reviews("SYN-A", "who,when\nREV-01,2026-11-01\n");

        assertThat(validate()).singleElement().satisfies(problem -> {
            assertThat(problem.line()).isZero();
            assertThat(problem.message()).contains("missing required column(s): reviewer, reviewed_on");
        });
    }

    // ---------------------------------------------------------------- positions

    @Test
    void aHoleInThePositionsIsReportedOnTheTopicAfterIt() {
        subject("SYN-A", """
                code,name,position,effort_tier
                T01,Topic 01,1,SHORT
                T02,Topic 02,2,SHORT
                T03,Topic 03,4,SHORT
                """, null);

        assertThat(validate()).singleElement().satisfies(problem -> {
            assertThat(problem.file()).isEqualTo("SYN-A/topics.csv");
            assertThat(problem.line()).isEqualTo(4);
            assertThat(problem.message()).contains("have a hole", "position 2 (T02) is followed by 4",
                    "without skipping");
        });
    }

    @Test
    void positionsThatDoNotStartAtOneAreAHoleToo() {
        subject("SYN-A", """
                code,name,position,effort_tier
                T01,Topic 01,0,SHORT
                T02,Topic 02,1,SHORT
                """, null);

        assertThat(validate()).singleElement()
                .satisfies(problem -> assertThat(problem.message()).contains("the first position is 0"));
    }

    @Test
    void positionsOneToNInAnyRowOrderPass() {
        subject("SYN-A", """
                code,name,position,effort_tier
                T03,Topic 03,3,SHORT
                T01,Topic 01,1,SHORT
                T02,Topic 02,2,SHORT
                """, null);

        assertThat(validate()).isEmpty();
    }

    @Test
    void aRepeatedPositionIsReportedOnceAndNotAlsoAsAHole() {
        subject("SYN-A", """
                code,name,position,effort_tier
                T01,Topic 01,1,SHORT
                T02,Topic 02,2,SHORT
                T03,Topic 03,2,SHORT
                T04,Topic 04,3,SHORT
                """, null);

        assertThat(validate()).singleElement()
                .satisfies(problem -> assertThat(problem.message()).contains("position 2 is already taken"));
    }

    // ---------------------------------------------------------------- codes and bands

    @Test
    void aDuplicateTopicCodeIsReportedAndAUniqueOnePasses() {
        subject("SYN-A", """
                code,name,position,effort_tier
                T01,Topic 01,1,SHORT
                T01,Topic 01 again,2,SHORT
                """, null);
        assertThat(validate()).singleElement()
                .satisfies(problem -> assertThat(problem.message()).contains("duplicate topic code T01",
                        "rename one of the two"));

        subject("SYN-A", """
                code,name,position,effort_tier
                T01,Topic 01,1,SHORT
                T02,Topic 02,2,SHORT
                """, null);
        assertThat(validate()).isEmpty();
    }

    @Test
    void anEffortBandOutsideTheSetIsReportedWithTheAcceptedValues() {
        subject("SYN-A", """
                code,name,position,effort_tier
                T01,Topic 01,1,45
                """, null);

        assertThat(validate()).singleElement().satisfies(problem -> {
            assertThat(problem.line()).isEqualTo(2);
            assertThat(problem.message()).contains("effort_tier is \"45\"",
                    "SHORT, STANDARD, LONG, EXTENDED");
        });
    }

    // ---------------------------------------------------------------- across subjects

    /**
     * Regression: an edge across subjects written in the dependent's file was reported against
     * the prerequisite's file, at a line number that belongs to the other file.
     */
    @Test
    void aProblemOnAnEdgeAcrossSubjectsPointsAtTheFileTheEdgeIsWrittenIn() {
        subject("SYN-A", THREE_TOPICS, null);
        subject("SYN-B", THREE_TOPICS, """
                prerequisite,dependent,strength,provenance,source_reference
                SYN-B:T01,SYN-B:T02,HARD,PROPOSED,
                SYN-A:T09,SYN-B:T03,HARD,PROPOSED,
                """);

        assertThat(validate()).singleElement().satisfies(problem -> {
            assertThat(problem.file()).isEqualTo("SYN-B/prerequisites.csv");
            assertThat(problem.line()).isEqualTo(3);
            assertThat(problem.message()).contains("unknown topic SYN-A:T09",
                    "no code T09 in SYN-A/topics.csv");
        });
    }

    @Test
    void anEdgeIntoASubjectThatWasNotReadSaysToRunBothSubjectsTogether() {
        subject("SYN-B", THREE_TOPICS, """
                prerequisite,dependent,strength,provenance,source_reference
                SYN-A:T01,SYN-B:T01,HARD,PROPOSED,
                """);

        assertThat(validate()).singleElement()
                .satisfies(problem -> assertThat(problem.message()).contains(
                        "subject SYN-A was not read", "run without --subject"));
    }

    @Test
    void anEdgeIntoAnotherSubjectThatIsReadPasses() {
        subject("SYN-A", THREE_TOPICS, null);
        subject("SYN-B", THREE_TOPICS, """
                prerequisite,dependent,strength,provenance,source_reference
                SYN-A:T03,SYN-B:T01,HARD,PROPOSED,
                """);

        assertThat(validate()).isEmpty();
    }

    /**
     * The new case a catalogue of several subjects brings: a loop that no single subject's file
     * shows, because it closes only by going from one subject to the other and back.
     */
    @Test
    void aCycleThatClosesOnlyAcrossSubjectsIsReportedAsTheWholePath() {
        subject("SYN-A", THREE_TOPICS, """
                prerequisite,dependent,strength,provenance,source_reference
                SYN-A:T01,SYN-A:T02,HARD,PROPOSED,
                SYN-A:T02,SYN-B:T01,HARD,PROPOSED,
                """);
        subject("SYN-B", THREE_TOPICS, """
                prerequisite,dependent,strength,provenance,source_reference
                SYN-B:T01,SYN-B:T02,SOFT,PROPOSED,
                SYN-B:T02,SYN-A:T01,HARD,PROPOSED,
                """);

        List<CatalogProblem> problems = validate();

        assertThat(problems).singleElement().satisfies(problem -> {
            assertThat(problem.message()).startsWith("cycle detected:")
                    .contains("SYN-A:T01", "SYN-A:T02", "SYN-B:T01", "SYN-B:T02", "Cut one edge");
            assertThat(problem.line()).isPositive();
        });
    }

    @Test
    void theSameTwoSubjectsWithoutTheEdgeBackAreAcyclic() {
        subject("SYN-A", THREE_TOPICS, """
                prerequisite,dependent,strength,provenance,source_reference
                SYN-A:T01,SYN-A:T02,HARD,PROPOSED,
                SYN-A:T02,SYN-B:T01,HARD,PROPOSED,
                """);
        subject("SYN-B", THREE_TOPICS, """
                prerequisite,dependent,strength,provenance,source_reference
                SYN-B:T01,SYN-B:T02,SOFT,PROPOSED,
                """);

        assertThat(validate()).isEmpty();
    }

    // ---------------------------------------------------------------- warnings

    @Test
    void aTopicWithNoEdgeAtAllIsAWarningAndNotAnError() {
        subject("SYN-A", THREE_TOPICS, """
                prerequisite,dependent,strength,provenance,source_reference
                SYN-A:T01,SYN-A:T02,HARD,PROPOSED,
                """);

        assertThat(validate()).isEmpty();
        assertThat(CatalogValidation.warnings(read())).singleElement().satisfies(warning -> {
            assertThat(warning.file()).isEqualTo("SYN-A/topics.csv");
            assertThat(warning.line()).isEqualTo(4);
            assertThat(warning.message()).contains("topic T03 has no prerequisite and nothing depends on it");
        });
    }

    @Test
    void anEdgeInEitherDirectionOrFromAnotherSubjectConnectsATopic() {
        subject("SYN-A", THREE_TOPICS, """
                prerequisite,dependent,strength,provenance,source_reference
                SYN-A:T01,SYN-A:T02,HARD,PROPOSED,
                """);
        subject("SYN-B", """
                code,name,position,effort_tier
                T01,Topic 01,1,SHORT
                """, """
                prerequisite,dependent,strength,provenance,source_reference
                SYN-A:T03,SYN-B:T01,SOFT,PROPOSED,
                """);

        assertThat(CatalogValidation.warnings(read())).isEmpty();
    }

    // ---------------------------------------------------------------- the example catalogue

    /**
     * The example shipped in {@code catalog/} has never been reviewed by anyone who teaches the
     * subject, so it may carry no {@code CURATED} edge and no review record. If this fails
     * because somebody recorded a real review, the README of {@code catalog/} has to change too.
     */
    @Test
    void theExampleCatalogueIsProposedThroughoutBecauseNobodyReviewedIt() {
        DesiredCatalogue example = CatalogFiles.read(Path.of("catalog"), null);

        assertThat(CatalogValidation.validate(example)).isEmpty();
        assertThat(example.reviews()).isEmpty();
        assertThat(example.edges()).isNotEmpty()
                .extracting(edge -> edge.provenance())
                .doesNotContain(EdgeProvenance.CURATED);
    }

    // ---------------------------------------------------------------- helpers

    private List<CatalogProblem> validate() {
        return CatalogValidation.validate(read());
    }

    private DesiredCatalogue read() {
        return CatalogFiles.read(root, null);
    }

    private void subject(String code, String topics, String prerequisites) {
        try {
            Path directory = Files.createDirectories(root.resolve(code));
            Files.writeString(directory.resolve("topics.csv"), topics, StandardCharsets.UTF_8);
            Path edges = directory.resolve("prerequisites.csv");
            if (prerequisites == null) {
                Files.deleteIfExists(edges);
            } else {
                Files.writeString(edges, prerequisites, StandardCharsets.UTF_8);
            }
        } catch (IOException unwritable) {
            throw new UncheckedIOException(unwritable);
        }
    }

    private void reviews(String code, String content) {
        try {
            Files.writeString(root.resolve(code).resolve("reviews.csv"), content, StandardCharsets.UTF_8);
        } catch (IOException unwritable) {
            throw new UncheckedIOException(unwritable);
        }
    }
}
