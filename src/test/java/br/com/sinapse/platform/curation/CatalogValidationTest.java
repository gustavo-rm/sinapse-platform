package br.com.sinapse.platform.curation;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.sinapse.platform.curation.internal.CatalogFiles;
import br.com.sinapse.platform.curation.internal.CatalogValidation;
import br.com.sinapse.platform.curation.internal.model.CatalogProblem;
import br.com.sinapse.platform.curation.internal.model.DesiredCatalogue;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validation, which is where this tool earns its place.
 *
 * <p>No database anywhere in this class, deliberately: every rule here is a property of the
 * files, and the whole reason for checking them in memory is that the database can only report
 * the first offending edge and cannot report a path at all.
 */
class CatalogValidationTest {

    private static final Logger LOG = LoggerFactory.getLogger(CatalogValidationTest.class);

    @TempDir
    Path root;

    /**
     * The check the definition of done names first, and the reason the tool exists.
     *
     * <p>The trigger would raise on whichever edge happened to be inserted last and name only
     * that one. What a curator needs is the loop, because the fix is to cut one edge of it and
     * they cannot choose which without seeing all of them.
     */
    @Test
    void aThreeEdgeCycleIsReportedAsTheWholePath() {
        subject("MED-ANAT", """
                code,name,position,effort_tier
                osteologia,Osteologia,1,STANDARD
                artrologia,Artrologia,2,STANDARD
                miologia,Miologia,3,STANDARD
                """, """
                prerequisite,dependent,strength,provenance,source_reference
                MED-ANAT:osteologia,MED-ANAT:artrologia,HARD,CURATED,
                MED-ANAT:artrologia,MED-ANAT:miologia,HARD,CURATED,
                MED-ANAT:miologia,MED-ANAT:osteologia,HARD,CURATED,
                """);

        List<CatalogProblem> problems = validate();

        assertThat(problems).hasSize(1);
        String reported = problems.getFirst().message();
        assertThat(reported).startsWith("cycle detected:");
        assertThat(reported).contains("MED-ANAT:osteologia", "MED-ANAT:artrologia", "MED-ANAT:miologia");
        assertThat(reported.lines().filter(line -> line.contains("MED-ANAT:")).count())
                .as("three topics and the one it closes back onto, so the loop reads as a loop")
                .isEqualTo(4);
        assertThat(reported).contains("(Osteologia)", "(Artrologia)", "(Miologia)");
        assertThat(problems.getFirst().line())
                .as("the line of the edge that closes it, which is the one to consider cutting")
                .isPositive();

        // Printed because the shape of this message is the deliverable, not an implementation
        // detail: a curator reads it and decides which edge to cut.
        LOG.info("Cycle report as a curator sees it:\n    {}", problems.getFirst());
    }

    /** One cycle is one problem, however many edges it runs through. */
    @Test
    void aCycleIsReportedOnceRatherThanOncePerEdgeOnIt() {
        subject("SUB", """
                code,name,position,effort_tier
                a,A,1,SHORT
                b,B,2,SHORT
                c,C,3,SHORT
                d,D,4,SHORT
                """, """
                prerequisite,dependent,strength,provenance,source_reference
                SUB:a,SUB:b,HARD,CURATED,
                SUB:b,SUB:c,HARD,CURATED,
                SUB:c,SUB:d,HARD,CURATED,
                SUB:d,SUB:a,HARD,CURATED,
                """);

        assertThat(validate())
                .as("four edges, one loop, one message")
                .hasSize(1);
    }

    /**
     * Five distinct errors, all in one run, each with its line.
     *
     * <p>Stopping at the first would turn a four-hundred-row export into forty runs, and the
     * curator who has to do that stops running the validator at all.
     */
    @Test
    void fiveDistinctErrorsAreAllReportedInOneRunWithTheirLines() {
        subject("SUB", """
                code,name,position,effort_tier
                a,A,1,SHORT
                b,B,1,STANDARD
                a,A de novo,3,SHORT
                d,D,4,ENORMOUS
                e,,5,SHORT
                """, """
                prerequisite,dependent,strength,provenance,source_reference
                SUB:a,SUB:a,HARD,CURATED,
                SUB:a,SUB:zzz,HARD,CURATED,
                SUB:a,SUB:b,MAYBE,CURATED,
                """);

        List<CatalogProblem> problems = validate();

        assertThat(problems).hasSizeGreaterThanOrEqualTo(5);
        assertThat(problems).allSatisfy(problem -> assertThat(problem.line()).isPositive());

        // Printed for the same reason as the cycle: the report is what a curator works from.
        LOG.info("Multi-error report as a curator sees it:\n    {}", problems.stream()
                .map(Object::toString)
                .collect(java.util.stream.Collectors.joining("\n    ")));
        assertThat(messages(problems)).anySatisfy(m -> assertThat(m).contains("position 1 is already taken"));
        assertThat(messages(problems)).anySatisfy(m -> assertThat(m).contains("duplicate topic code a"));
        assertThat(messages(problems)).anySatisfy(m -> assertThat(m).contains("effort_tier is not one of"));
        assertThat(messages(problems)).anySatisfy(m -> assertThat(m).contains("name is blank"));
        assertThat(messages(problems)).anySatisfy(m -> assertThat(m).contains("cannot be its own prerequisite"));
        assertThat(messages(problems)).anySatisfy(m -> assertThat(m).contains("unknown topic SUB:zzz"));
        assertThat(messages(problems)).anySatisfy(m -> assertThat(m).contains("strength is not HARD or SOFT"));
    }

    @Test
    void problemsComeBackInFileAndLineOrderSoTheyCanBeWorkedThroughTopDown() {
        subject("SUB", """
                code,name,position,effort_tier
                a,A,1,SHORT
                b,B,2,NOPE
                c,C,3,ALSO_NOPE
                """, """
                prerequisite,dependent,strength,provenance,source_reference
                """);

        assertThat(validate()).extracting(CatalogProblem::line).containsExactly(3, 4);
    }

    @Test
    void aMalformedRowIsReportedRatherThanQuietlyMisread() {
        subject("SUB", """
                code,name,position,effort_tier
                a,A,1,SHORT
                b,"B que nunca fecha,2,SHORT
                c,C,3,SHORT,extra
                """, """
                prerequisite,dependent,strength,provenance,source_reference
                """);

        assertThat(messages(validate()))
                .allSatisfy(message -> assertThat(message).contains("malformed row"))
                .hasSize(2);
    }

    @Test
    void aMissingColumnStopsTheFileRatherThanProducingAnErrorPerRow() {
        subject("SUB", """
                code,name,position
                a,A,1
                b,B,2
                """, """
                prerequisite,dependent,strength,provenance,source_reference
                """);

        assertThat(messages(validate()))
                .singleElement()
                .satisfies(message -> assertThat(message).contains("missing required column(s): effort_tier"));
    }

    /** A quoted name with a comma is what a spreadsheet exports, and it has to survive. */
    @Test
    void aQuotedNameWithACommaIsReadWhole() {
        subject("SUB", """
                code,name,position,effort_tier
                a,"Osteologia, artrologia e miologia",1,SHORT
                """, """
                prerequisite,dependent,strength,provenance,source_reference
                """);

        DesiredCatalogue desired = CatalogFiles.read(root, null);

        assertThat(validate()).isEmpty();
        assertThat(desired.topics()).singleElement()
                .satisfies(topic -> assertThat(topic.name())
                        .isEqualTo("Osteologia, artrologia e miologia"));
    }

    /** An edge may cross subjects: that is what the natural key is for. */
    @Test
    void anEdgeAcrossTwoSubjectsIsValid() {
        subject("MED-ANAT", """
                code,name,position,effort_tier
                cranio,Crânio,1,STANDARD
                """, """
                prerequisite,dependent,strength,provenance,source_reference
                """);
        subject("MED-FISIO", """
                code,name,position,effort_tier
                neuro,Neurofisiologia,1,STANDARD
                """, """
                prerequisite,dependent,strength,provenance,source_reference
                MED-ANAT:cranio,MED-FISIO:neuro,HARD,CURATED,
                """);

        assertThat(validate()).isEmpty();
    }

    /** Reading one subject must not report the other subject's topics as unknown. */
    @Test
    void anUnknownReferenceSaysWhenTheSubjectItselfWasNotRead() {
        subject("MED-ANAT", """
                code,name,position,effort_tier
                cranio,Crânio,1,STANDARD
                """, """
                prerequisite,dependent,strength,provenance,source_reference
                MED-FISIO:neuro,MED-ANAT:cranio,HARD,CURATED,
                """);

        assertThat(messages(validate())).singleElement().satisfies(message ->
                assertThat(message).contains("unknown topic MED-FISIO:neuro", "was not read"));
    }

    @Test
    void theSamePairDeclaredTwiceIsReportedWithTheLineItFirstAppearedOn() {
        subject("SUB", """
                code,name,position,effort_tier
                a,A,1,SHORT
                b,B,2,SHORT
                """, """
                prerequisite,dependent,strength,provenance,source_reference
                SUB:a,SUB:b,HARD,CURATED,
                SUB:a,SUB:b,SOFT,CURATED,
                """);

        assertThat(messages(validate())).singleElement().satisfies(message ->
                assertThat(message).contains("duplicate edge SUB:a -> SUB:b", "line 2"));
    }

    /** The example catalogue in this repository has to be valid, or it teaches the wrong shape. */
    @Test
    void theExampleCatalogueShippedWithTheRepositoryValidates() {
        Path shipped = Path.of("catalog");
        DesiredCatalogue desired = CatalogFiles.read(shipped, null);

        assertThat(desired.subjects()).containsKey("MED-ANAT");
        assertThat(desired.topics()).hasSizeGreaterThanOrEqualTo(30);
        assertThat(desired.edges())
                .as("ADR 0014 asks for one pilot subject of about thirty edges before scaling")
                .hasSizeGreaterThanOrEqualTo(30);
        assertThat(CatalogValidation.validate(desired)).isEmpty();
    }

    private List<CatalogProblem> validate() {
        return CatalogValidation.validate(CatalogFiles.read(root, null));
    }

    private static List<String> messages(List<CatalogProblem> problems) {
        return problems.stream().map(CatalogProblem::message).toList();
    }

    private void subject(String code, String topics, String prerequisites) {
        try {
            Path directory = Files.createDirectories(root.resolve(code));
            Files.writeString(directory.resolve("topics.csv"), topics, StandardCharsets.UTF_8);
            Files.writeString(directory.resolve("prerequisites.csv"), prerequisites,
                    StandardCharsets.UTF_8);
        } catch (IOException unwritable) {
            throw new UncheckedIOException(unwritable);
        }
    }

    /** A hidden directory is not a subject: a catalogue root is often a git tree of its own. */
    @Test
    void hiddenDirectoriesAreNotReadAsSubjects() {
        subject("SUB", """
                code,name,position,effort_tier
                a,A,1,SHORT
                """, """
                prerequisite,dependent,strength,provenance,source_reference
                """);
        try {
            Files.createDirectories(root.resolve(".git").resolve("objects"));
            Files.createDirectories(root.resolve(".idea"));
        } catch (IOException unwritable) {
            throw new UncheckedIOException(unwritable);
        }

        assertThat(CatalogFiles.read(root, null).subjects()).containsOnlyKeys("SUB");
        assertThat(validate()).isEmpty();
    }

    /** A file where a data row has one cell too many is malformed, not silently truncated. */
    @Test
    void anEdgeReferenceThatIsNotSubjectColonTopicIsReported() {
        subject("SUB", """
                code,name,position,effort_tier
                a,A,1,SHORT
                b,B,2,SHORT
                """, """
                prerequisite,dependent,strength,provenance,source_reference
                a,SUB:b,HARD,CURATED,
                SUB:a,SUB:b:c,HARD,CURATED,
                """);

        assertThat(messages(validate()))
                .allSatisfy(message -> assertThat(message)
                        .contains("must both read subject_code:topic_code"))
                .hasSize(2);
    }

    @Test
    void aBlankCodeOrAnUnreadablePositionIsReportedWithItsLine() {
        subject("SUB", """
                code,name,position,effort_tier
                ,Sem código,1,SHORT
                b,B,muitas,SHORT
                c,C,-3,SHORT
                """, """
                prerequisite,dependent,strength,provenance,source_reference
                """);

        assertThat(messages(validate())).satisfiesExactly(
                first -> assertThat(first).contains("code is blank"),
                second -> assertThat(second).contains("position is not a whole number"),
                third -> assertThat(third).contains("position is not a whole number"));
    }

    /** An unknown provenance is refused rather than defaulted to CURATED. */
    @Test
    void anUnknownProvenanceIsReported() {
        subject("SUB", """
                code,name,position,effort_tier
                a,A,1,SHORT
                b,B,2,SHORT
                """, """
                prerequisite,dependent,strength,provenance,source_reference
                SUB:a,SUB:b,HARD,INVENTADO,
                """);

        assertThat(messages(validate())).singleElement().satisfies(message ->
                assertThat(message).contains("provenance is not one of"));
    }

    /** Two independent cycles are two problems, and both are named in full. */
    @Test
    void twoSeparateCyclesAreBothReported() {
        subject("SUB", """
                code,name,position,effort_tier
                a,A,1,SHORT
                b,B,2,SHORT
                c,C,3,SHORT
                d,D,4,SHORT
                """, """
                prerequisite,dependent,strength,provenance,source_reference
                SUB:a,SUB:b,HARD,CURATED,
                SUB:b,SUB:a,HARD,CURATED,
                SUB:c,SUB:d,SOFT,TEXTBOOK_ORDER,
                SUB:d,SUB:c,SOFT,TEXTBOOK_ORDER,
                """);

        List<CatalogProblem> problems = validate();

        assertThat(problems).hasSize(2);
        assertThat(messages(problems)).allSatisfy(message ->
                assertThat(message).startsWith("cycle detected:"));
    }

    /** A soft cycle is still a cycle: the trigger does not distinguish them either. */
    @Test
    void aCycleOfSoftEdgesIsStillRefused() {
        subject("SUB", """
                code,name,position,effort_tier
                a,A,1,SHORT
                b,B,2,SHORT
                """, """
                prerequisite,dependent,strength,provenance,source_reference
                SUB:a,SUB:b,SOFT,TEXTBOOK_ORDER,
                SUB:b,SUB:a,SOFT,TEXTBOOK_ORDER,
                """);

        assertThat(messages(validate())).singleElement().satisfies(message ->
                assertThat(message).startsWith("cycle detected:"));
    }
}
