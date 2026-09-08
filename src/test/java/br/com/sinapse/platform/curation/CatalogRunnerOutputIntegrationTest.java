package br.com.sinapse.platform.curation;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.sinapse.platform.curation.internal.CatalogApplier;
import br.com.sinapse.platform.curation.internal.CatalogCommand;
import br.com.sinapse.platform.curation.internal.CatalogDiffer;
import br.com.sinapse.platform.curation.internal.CatalogRunner;
import br.com.sinapse.platform.identity.internal.domain.Account;
import br.com.sinapse.platform.learningrecord.api.RecallRating;
import br.com.sinapse.platform.learningrecord.api.SessionKind;
import br.com.sinapse.platform.learningrecord.api.StudySessionView;
import br.com.sinapse.platform.learningrecord.internal.service.StudySessionService;
import br.com.sinapse.platform.planning.support.PlanningIntegrationTest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;

/**
 * What each command prints, and what it returns.
 *
 * <p>The exit code matters as much as the text: a pipeline that runs {@code catalog validate}
 * has nothing else to go on, and a validator that reports problems and exits zero is a validator
 * nobody's build will notice.
 */
class CatalogRunnerOutputIntegrationTest extends PlanningIntegrationTest {

    @TempDir
    Path root;

    @Autowired
    private CatalogDiffer differ;

    @Autowired
    private CatalogApplier applier;

    @Autowired
    private StudySessionService studySessions;

    private final List<String> printed = new ArrayList<>();

    @Test
    void badUsageIsAnsweredWithTheUsageAndItsOwnExitCode() {
        assertThat(runner().run((CatalogCommand) null)).isEqualTo(2);
        assertThat(printed).anySatisfy(line -> assertThat(line).contains("catalog validate"));
        assertThat(printed).anySatisfy(line -> assertThat(line).contains("--allow-topic-removal"));
    }

    @Test
    void validateReportsProblemsAndExitsNonZeroSoAPipelineNotices() {
        write("SUB", """
                code,name,position,effort_tier
                a,A,1,NOPE
                """, "prerequisite,dependent,strength,provenance,source_reference\n");

        assertThat(runner().run(command(CatalogCommand.Verb.VALIDATE))).isEqualTo(1);
        assertThat(printed).anySatisfy(line -> assertThat(line).contains("1 problem(s)"));
    }

    @Test
    void aMissingCatalogueDirectoryIsSaidPlainlyRatherThanThrown() {
        CatalogRunner runner = new CatalogRunner(differ, applier, root.resolve("absent"),
                printed::add);

        assertThat(runner.run(command(CatalogCommand.Verb.VALIDATE))).isEqualTo(1);
        assertThat(printed).anySatisfy(line ->
                assertThat(line).contains("the catalogue directory does not exist"));
    }

    @Test
    void anEmptyCatalogueDirectoryIsSaidPlainly() {
        assertThat(runner().run(command(CatalogCommand.Verb.VALIDATE))).isEqualTo(1);
        assertThat(printed).anySatisfy(line ->
                assertThat(line).contains("no subject directories found"));
    }

    @Test
    void aSubjectWithNoPrerequisitesFileIsValidBecauseAGraphStartsEmpty() {
        Path directory = directory("SUB");
        writeFile(directory.resolve("topics.csv"), """
                code,name,position,effort_tier
                a,A,1,SHORT
                """);

        assertThat(runner().run(command(CatalogCommand.Verb.VALIDATE))).isZero();
        assertThat(printed).anySatisfy(line -> assertThat(line).contains("no problems found"));
    }

    @Test
    void aSubjectWithNoTopicsFileIsReported() {
        directory("SUB");

        assertThat(runner().run(command(CatalogCommand.Verb.VALIDATE))).isEqualTo(1);
        assertThat(printed).anySatisfy(line -> assertThat(line).contains("the file is missing"));
    }

    /** The optional third file, and the only way to give a subject a display name. */
    @Test
    void theSubjectNameComesFromItsOwnFileWhenThereIsOneAndFromTheCodeOtherwise() {
        Path directory = directory("MED-ANAT");
        writeFile(directory.resolve("topics.csv"), """
                code,name,position,effort_tier
                a,A,1,SHORT
                """);

        assertThat(runner().run(command(CatalogCommand.Verb.APPLY))).isZero();
        assertThat(subjectName("MED-ANAT"))
                .as("without subject.csv the code is the name, which is what ADR 0014's two "
                        + "files can express and nothing more")
                .isEqualTo("MED-ANAT");

        writeFile(directory.resolve("subject.csv"), "name\nAnatomia Humana\n");
        printed.clear();
        assertThat(runner().run(command(CatalogCommand.Verb.APPLY))).isZero();
        assertThat(subjectName("MED-ANAT")).isEqualTo("Anatomia Humana");
    }

    @Test
    void aSubjectFileWithoutItsColumnOrItsRowIsReported() {
        Path directory = directory("SUB");
        writeFile(directory.resolve("topics.csv"), """
                code,name,position,effort_tier
                a,A,1,SHORT
                """);
        writeFile(directory.resolve("subject.csv"), "nome\nAnatomia\n");

        assertThat(runner().run(command(CatalogCommand.Verb.VALIDATE))).isEqualTo(1);
        assertThat(printed).anySatisfy(line ->
                assertThat(line).contains("missing required column: name"));

        writeFile(directory.resolve("subject.csv"), "name\n");
        printed.clear();
        assertThat(runner().run(command(CatalogCommand.Verb.VALIDATE))).isEqualTo(1);
        assertThat(printed).anySatisfy(line -> assertThat(line).contains("expected exactly one"));
    }

    /** A run narrowed to one subject leaves every other subject entirely alone. */
    @Test
    void narrowingToOneSubjectReadsAndTouchesOnlyThatOne() {
        write("ONE", """
                code,name,position,effort_tier
                a,A,1,SHORT
                """, "prerequisite,dependent,strength,provenance,source_reference\n");
        write("TWO", """
                code,name,position,effort_tier
                b,B,1,SHORT
                """, "prerequisite,dependent,strength,provenance,source_reference\n");

        assertThat(runner().run(new CatalogCommand(CatalogCommand.Verb.APPLY, "ONE", false)))
                .isZero();

        assertThat(subjectExists("ONE")).isTrue();
        assertThat(subjectExists("TWO")).isFalse();
    }

    @Test
    void narrowingToASubjectThatHasNoDirectorySaysSo() {
        write("ONE", """
                code,name,position,effort_tier
                a,A,1,SHORT
                """, "prerequisite,dependent,strength,provenance,source_reference\n");

        assertThat(runner().run(new CatalogCommand(CatalogCommand.Verb.VALIDATE, "MISSING", false)))
                .isEqualTo(1);
        assertThat(printed).anySatisfy(line ->
                assertThat(line).contains("no directory for subject MISSING"));
    }

    @Test
    void seedOrderRefusesFilesThatDoNotValidateAndReportsWhatItWroteWhenTheyDo() {
        write("SUB", """
                code,name,position,effort_tier
                a,A,1,SHORT
                b,B,1,SHORT
                """, "prerequisite,dependent,strength,provenance,source_reference\n");

        assertThat(runner().run(new CatalogCommand(CatalogCommand.Verb.SEED_ORDER, "SUB", false)))
                .isEqualTo(1);
        assertThat(printed).anySatisfy(line -> assertThat(line).contains("refused"));

        write("SUB", """
                code,name,position,effort_tier
                a,A,1,SHORT
                b,B,2,SHORT
                """, "prerequisite,dependent,strength,provenance,source_reference\n");
        printed.clear();

        assertThat(runner().run(new CatalogCommand(CatalogCommand.Verb.SEED_ORDER, "SUB", false)))
                .isZero();
        assertThat(printed).anySatisfy(line -> assertThat(line).contains("1 edge(s) written"));
        assertThat(printed).anySatisfy(line -> assertThat(line).contains("Run \"catalog apply\""));
    }

    /** The diff a curator reads before applying anything, with a topic they took out. */
    @Test
    void theDiffNamesWhatWouldChangeAndWhatWouldBeKept() {
        write("SUB", """
                code,name,position,effort_tier
                a,A,1,SHORT
                b,B,2,SHORT
                """, """
                prerequisite,dependent,strength,provenance,source_reference
                SUB:a,SUB:b,HARD,CURATED,
                """);
        runner().run(command(CatalogCommand.Verb.APPLY));

        write("SUB", """
                code,name,position,effort_tier
                a,A renomeado,1,LONG
                """, "prerequisite,dependent,strength,provenance,source_reference\n");
        printed.clear();

        assertThat(runner().run(command(CatalogCommand.Verb.DIFF))).isZero();

        assertThat(printed).anySatisfy(line -> assertThat(line)
                .contains("~ topic  a", "name \"A\" -> \"A renomeado\"", "effort SHORT -> LONG"));
        assertThat(printed).anySatisfy(line -> assertThat(line).contains("- edge   SUB:a -> SUB:b"));
        assertThat(printed).anySatisfy(line -> assertThat(line).contains("? topic  b  B"));
        assertThat(printed).anySatisfy(line ->
                assertThat(line).contains("--allow-topic-removal"));
    }

    private CatalogRunner runner() {
        return new CatalogRunner(differ, applier, root, printed::add);
    }

    private static CatalogCommand command(CatalogCommand.Verb verb) {
        return new CatalogCommand(verb, null, false);
    }

    private String subjectName(String code) {
        return jdbc.queryForObject("select name from subject where code = ?", String.class, code);
    }

    private boolean subjectExists(String code) {
        return jdbc.queryForObject("select count(*) from subject where code = ?", Integer.class,
                code) > 0;
    }

    private Path directory(String code) {
        try {
            return Files.createDirectories(root.resolve(code));
        } catch (IOException unwritable) {
            throw new UncheckedIOException(unwritable);
        }
    }

    private void write(String code, String topics, String prerequisites) {
        Path directory = directory(code);
        writeFile(directory.resolve("topics.csv"), topics);
        writeFile(directory.resolve("prerequisites.csv"), prerequisites);
    }

    private static void writeFile(Path file, String content) {
        try {
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException unwritable) {
            throw new UncheckedIOException(unwritable);
        }
    }

    /**
     * The runner as Spring actually invokes it, arguments and exit code included.
     *
     * <p>Everything else here calls {@code run(CatalogCommand)} directly, which skips the one
     * piece of glue an operator depends on: turning {@code --subject=X} on a command line into
     * the command, and turning the outcome into a process exit code.
     */
    @Test
    void springInvokesItWithTheArgumentsAndReadsBackAnExitCode() {
        write("SUB", """
                code,name,position,effort_tier
                a,A,1,SHORT
                """, "prerequisite,dependent,strength,provenance,source_reference\n");
        CatalogRunner runner = runner();

        runner.run(new DefaultApplicationArguments("catalog", "apply", "--subject=SUB"));

        assertThat(runner.getExitCode()).isZero();
        assertThat(subjectExists("SUB")).isTrue();
        assertThat(printed).anySatisfy(line -> assertThat(line).contains("recorded as import"));
    }

    @Test
    void anUnreadableCommandLineExitsWithItsOwnCodeRatherThanZero() {
        CatalogRunner runner = runner();

        runner.run(new DefaultApplicationArguments("catalog", "aply"));

        assertThat(runner.getExitCode()).isEqualTo(2);
        assertThat(printed).anySatisfy(line -> assertThat(line).contains("usage:"));
    }

    /**
     * Applying from a working tree with uncommitted changes warns and records the suffix.
     *
     * <p>The warning is the deliverable. A revision that does not describe what was applied
     * breaks the whole provenance argument of ADR 0014, and it breaks it silently unless
     * somebody says so at the moment it happens.
     */
    @Test
    void applyingFromADirtyTreeWarnsAndRecordsTheSuffix() throws Exception {
        Path tree = Files.createDirectories(root.resolve("tree"));
        git(tree, "init", "--initial-branch=main");
        git(tree, "config", "user.email", "curator@example.test");
        git(tree, "config", "user.name", "Curator");
        Files.createDirectories(tree.resolve("SUB"));
        writeFile(tree.resolve("SUB").resolve("topics.csv"), """
                code,name,position,effort_tier
                a,A,1,SHORT
                """);
        writeFile(tree.resolve("SUB").resolve("prerequisites.csv"),
                "prerequisite,dependent,strength,provenance,source_reference\n");
        git(tree, "add", ".");
        git(tree, "commit", "-m", "seed");
        writeFile(tree.resolve("SUB").resolve("topics.csv"), """
                code,name,position,effort_tier
                a,A editado sem commit,1,SHORT
                """);

        CatalogRunner runner = new CatalogRunner(differ, applier, tree, printed::add);
        assertThat(runner.run(command(CatalogCommand.Verb.APPLY))).isZero();

        assertThat(printed).anySatisfy(line ->
                assertThat(line).contains("the catalogue has uncommitted changes"));
        assertThat(jdbc.queryForObject(
                "select source_revision from catalog_import order by applied_at desc limit 1",
                String.class))
                .endsWith("-dirty");
    }

    /** A refused removal is reported as something a curator can act on, and nothing changed. */
    @Test
    void aRemovalRefusedByStudyHistoryIsExplainedAndNothingIsWritten() {
        write("SUB", """
                code,name,position,effort_tier
                a,A,1,SHORT
                b,B,2,SHORT
                """, "prerequisite,dependent,strength,provenance,source_reference\n");
        runner().run(command(CatalogCommand.Verb.APPLY));

        UUID doomed = jdbc.queryForObject("select id from topic where code = 'b'", UUID.class);
        Account holder = identity.activeAdult(identity.uniqueEmail());
        StudySessionView started = studySessions.start(holder.id(), doomed, null,
                SessionKind.STUDY, 50);
        studySessions.complete(holder.id(), started.id(), RecallRating.GOOD, 45);

        write("SUB", """
                code,name,position,effort_tier
                a,A,1,SHORT
                """, "prerequisite,dependent,strength,provenance,source_reference\n");
        printed.clear();

        assertThat(runner().run(new CatalogCommand(CatalogCommand.Verb.APPLY, null, true)))
                .isEqualTo(1);

        assertThat(printed).anySatisfy(line -> assertThat(line).contains("rolled back"));
        assertThat(printed).anySatisfy(line ->
                assertThat(line).contains("Evidence is not deleted"));
        assertThat(printed).allSatisfy(line -> assertThat(line).doesNotContain("Exception"));
        assertThat(jdbc.queryForObject("select count(*) from topic where code = 'b'", Integer.class))
                .isEqualTo(1);
    }

    private static void git(Path directory, String... arguments) throws Exception {
        List<String> command = new ArrayList<>(List.of("git"));
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).directory(directory.toFile())
                .redirectErrorStream(true).start();
        assertThat(process.waitFor()).isZero();
    }
}
