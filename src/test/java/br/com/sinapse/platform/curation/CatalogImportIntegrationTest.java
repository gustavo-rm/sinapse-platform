package br.com.sinapse.platform.curation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.sinapse.platform.curation.internal.CatalogApplier;
import br.com.sinapse.platform.curation.internal.CatalogCommand;
import br.com.sinapse.platform.curation.internal.CatalogDiff;
import br.com.sinapse.platform.curation.internal.CatalogDiffer;
import br.com.sinapse.platform.curation.internal.CatalogFiles;
import br.com.sinapse.platform.curation.internal.CatalogRunner;
import br.com.sinapse.platform.curation.internal.GitRevision;
import br.com.sinapse.platform.curation.internal.model.DesiredCatalogue;
import br.com.sinapse.platform.curriculum.api.CurriculumCatalog;
import br.com.sinapse.platform.curriculum.api.PrerequisiteGraph;
import br.com.sinapse.platform.curriculum.api.SubjectView;
import br.com.sinapse.platform.curriculum.api.TopicStillReferencedException;
import br.com.sinapse.platform.curriculum.api.TopicView;
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
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The importer against a real catalogue in a real database.
 *
 * <p>Nothing here is stubbed. The acyclicity trigger, the deferred unique constraint on position
 * and — the one that matters most for topic removal — the foreign keys from the learning record
 * and from planning are the ones that ship. That is the point: the tool's own validation is the
 * first line, and these are the last, and only a real database can show that the two agree.
 */
class CatalogImportIntegrationTest extends PlanningIntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(CatalogImportIntegrationTest.class);

    @TempDir
    Path root;

    @Autowired
    private CatalogDiffer differ;

    @Autowired
    private CatalogApplier applier;

    @Autowired
    private CurriculumCatalog catalog;

    @Autowired
    private PrerequisiteGraph graph;

    @Autowired
    private StudySessionService studySessions;

    /** The definition of done: the same file applied twice changes nothing the second time. */
    @Test
    void applyingTheSameFilesTwiceChangesNothingTheSecondTime() {
        threeTopicsAndTwoEdges();

        CatalogApplier.Applied first = apply(false);
        assertThat(first.diff().topicsAdded()).hasSize(3);
        assertThat(first.diff().edgesAdded()).hasSize(2);

        CatalogApplier.Applied second = apply(false);

        assertThat(second.diff().isEmpty())
                .as("declarative and idempotent: the file is the desired state, not a script")
                .isTrue();
        assertThat(second.diff().topicsUnchanged()).isEqualTo(3);
        assertThat(second.diff().edgesUnchanged()).isEqualTo(2);
        assertThat(topicsOf("SUB")).hasSize(3);
    }

    @Test
    void aRevisedNamePositionOrEffortBandIsAnUpdateAndNotADuplicate() {
        threeTopicsAndTwoEdges();
        apply(false);

        write("SUB", """
                code,name,position,effort_tier
                a,A revisado,1,LONG
                b,B,2,SHORT
                c,C,3,SHORT
                """, edges());
        CatalogDiff diff = differ.diff(read());

        assertThat(diff.topicsAdded()).isEmpty();
        assertThat(diff.topicsChanged()).singleElement().satisfies(change -> {
            assertThat(change.stored().name()).isEqualTo("A");
            assertThat(change.desired().name()).isEqualTo("A revisado");
        });

        apply(false);
        assertThat(topicsOf("SUB")).hasSize(3);
        assertThat(topicsOf("SUB").getFirst().name()).isEqualTo("A revisado");
    }

    /** Reordering is a change of positions, and the deferred constraint is what allows it. */
    @Test
    void aReorderThatSwapsTwoTopicsIsAppliedInOneTransaction() {
        threeTopicsAndTwoEdges();
        apply(false);

        write("SUB", """
                code,name,position,effort_tier
                a,A,2,SHORT
                b,B,1,SHORT
                c,C,3,SHORT
                """, edges());
        apply(false);

        assertThat(topicsOf("SUB")).extracting(TopicView::code).containsExactly("b", "a", "c");
    }

    /** An edge dropped from the file is removed; edges are curated data and correctable. */
    @Test
    void anEdgeAbsentFromTheFileIsRemoved() {
        threeTopicsAndTwoEdges();
        apply(false);
        assertThat(edgesOf("SUB")).hasSize(2);

        write("SUB", topics(), """
                prerequisite,dependent,strength,provenance,source_reference
                SUB:a,SUB:b,HARD,CURATED,
                """);
        CatalogApplier.Applied applied = apply(false);

        assertThat(applied.diff().edgesRemoved()).hasSize(1);
        assertThat(edgesOf("SUB")).hasSize(1);
    }

    /** The definition of done: absent means reported, not removed. */
    @Test
    void aTopicMissingFromTheFileIsReportedAndKept() {
        threeTopicsAndTwoEdges();
        apply(false);

        write("SUB", """
                code,name,position,effort_tier
                a,A,1,SHORT
                b,B,2,SHORT
                """, """
                prerequisite,dependent,strength,provenance,source_reference
                SUB:a,SUB:b,HARD,CURATED,
                """);
        CatalogApplier.Applied applied = apply(false);

        assertThat(applied.diff().topicsMissing()).singleElement()
                .satisfies(topic -> assertThat(topic.code()).isEqualTo("c"));
        assertThat(applied.removed()).isEmpty();
        assertThat(topicsOf("SUB"))
                .as("evidence does not disappear as a side effect of an import")
                .hasSize(3);
        assertThat(importRow(applied.importId()).get("notes").toString())
                .contains("were kept");
    }

    /** With the flag, and nothing referencing it, the topic really goes. */
    @Test
    void aTopicIsRemovedOnlyWhenRemovalIsAskedForByName() {
        threeTopicsAndTwoEdges();
        apply(false);

        write("SUB", """
                code,name,position,effort_tier
                a,A,1,SHORT
                b,B,2,SHORT
                """, """
                prerequisite,dependent,strength,provenance,source_reference
                SUB:a,SUB:b,HARD,CURATED,
                """);
        CatalogApplier.Applied applied = apply(true);

        assertThat(applied.removed()).singleElement()
                .satisfies(topic -> assertThat(topic.code()).isEqualTo("c"));
        assertThat(topicsOf("SUB")).extracting(TopicView::code).containsExactly("a", "b");
        assertThat(importRow(applied.importId()).get("notes").toString())
                .contains("removed on an explicit instruction");
    }

    /**
     * The definition of done: removal is refused when study history names the topic.
     *
     * <p>Curriculum does not know what a study session is, and must not (rule R3). The foreign
     * key is what refuses, and the refusal takes the whole run back.
     */
    @Test
    void removalIsRefusedWhenAStudySessionReferencesTheTopic() {
        threeTopicsAndTwoEdges();
        apply(false);
        TopicView doomed = topicsOf("SUB").getLast();

        Account holder = identity.activeAdult(identity.uniqueEmail());
        StudySessionView started = studySessions.start(holder.id(), doomed.id(), null,
                SessionKind.STUDY, 50);
        studySessions.complete(holder.id(), started.id(), RecallRating.GOOD, 45);

        write("SUB", """
                code,name,position,effort_tier
                a,A,1,SHORT
                b,B,2,SHORT
                """, """
                prerequisite,dependent,strength,provenance,source_reference
                SUB:a,SUB:b,HARD,CURATED,
                """);

        assertThatThrownBy(() -> apply(true)).isInstanceOf(TopicStillReferencedException.class);

        assertThat(topicsOf("SUB"))
                .as("the whole run went back, so the edge it would also have removed is still there")
                .hasSize(3);
        assertThat(edgesOf("SUB")).hasSize(2);
        assertThat(jdbc.queryForObject("select count(*) from study_session where topic_id = ?",
                Integer.class, doomed.id()))
                .isEqualTo(1);
    }

    /**
     * A failure part way through takes everything back, the import row included.
     *
     * <p>Forced with a cycle that the tool's own validation would have caught, so the trigger is
     * what raises — which is exactly the last line of defence being exercised, on a run that
     * has already written topics by the time it fires.
     */
    @Test
    void aFailureMidApplyRollsBackEverythingIncludingTheImportRow() {
        int importsBefore = importCount();
        write("SUB", """
                code,name,position,effort_tier
                a,A,1,SHORT
                b,B,2,SHORT
                c,C,3,SHORT
                """, """
                prerequisite,dependent,strength,provenance,source_reference
                SUB:a,SUB:b,HARD,CURATED,
                SUB:b,SUB:c,HARD,CURATED,
                SUB:c,SUB:a,HARD,CURATED,
                """);

        assertThatThrownBy(() -> apply(false)).isInstanceOf(RuntimeException.class);

        assertThat(catalog.subjectByCode("SUB"))
                .as("a partial import would leave the catalogue in a state no file describes")
                .isEmpty();
        assertThat(importCount()).isEqualTo(importsBefore);
    }

    /** The row that makes ADR 0014's provenance argument real. */
    @Test
    void theImportRowRecordsTheRevisionAndTheCounts() {
        threeTopicsAndTwoEdges();

        CatalogApplier.Applied applied = apply(false);

        Map<String, Object> row = importRow(applied.importId());
        assertThat(row.get("subjects_affected")).isEqualTo(1);
        assertThat(row.get("topics_added")).isEqualTo(3);
        assertThat(row.get("topics_updated")).isEqualTo(0);
        assertThat(row.get("edges_added")).isEqualTo(2);
        assertThat(row.get("edges_removed")).isEqualTo(0);
        assertThat(row.get("source_revision").toString())
                .as("a temp directory is outside any working tree, so git cannot name a revision "
                        + "and the row says so rather than inventing one")
                .isEqualTo("unknown");
        assertThat(row.get("applied_at")).isNotNull();
    }

    /**
     * The revision, resolved against a real git working tree.
     *
     * <p>Against a repository this test creates rather than against this one: asserting on the
     * checkout the suite happens to run in would fail in a shallow clone, in a tarball export,
     * and on the commit that first adds the catalogue. What matters is that a commit is named
     * when there is one, and that an uncommitted change is never passed off as that commit.
     */
    @Test
    void theRevisionNamesTheCommitAndSaysSoWhenTheTreeIsDirty() throws Exception {
        Path repository = Files.createDirectories(root.resolve("tree"));
        git(repository, "init", "--initial-branch=main");
        git(repository, "config", "user.email", "curator@example.test");
        git(repository, "config", "user.name", "Curator");
        Files.writeString(repository.resolve("topics.csv"), "code,name,position,effort_tier\n",
                StandardCharsets.UTF_8);
        git(repository, "add", ".");
        git(repository, "commit", "-m", "seed");

        String committed = GitRevision.of(repository);
        LOG.info("Revision resolved for a clean tree: {}", committed);
        assertThat(committed).hasSize(40).matches("[0-9a-f]{40}");

        Files.writeString(repository.resolve("topics.csv"),
                "code,name,position,effort_tier\na,A,1,SHORT\n", StandardCharsets.UTF_8);

        String dirty = GitRevision.of(repository);
        LOG.info("Revision resolved after an uncommitted edit: {}", dirty);
        assertThat(dirty)
                .as("no commit describes what would be applied, and the row has to say so")
                .isEqualTo(committed + "-dirty");
    }

    /** Outside any working tree there is no revision, and none is invented. */
    @Test
    void aDirectoryOutsideAWorkingTreeResolvesToUnknown() {
        assertThat(GitRevision.of(root)).isEqualTo("unknown");
    }

    private static void git(Path directory, String... arguments) throws Exception {
        List<String> command = new ArrayList<>(List.of("git"));
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).directory(directory.toFile())
                .redirectErrorStream(true).start();
        assertThat(process.waitFor()).as("git %s", String.join(" ", arguments)).isZero();
    }

    /** The runner, end to end, through the same code path an operator invokes. */
    @Test
    void theRunnerValidatesDiffsAndAppliesAndReportsWhatItDid() {
        threeTopicsAndTwoEdges();
        List<String> printed = new ArrayList<>();
        CatalogRunner runner = new CatalogRunner(differ, applier, root, printed::add);

        assertThat(runner.run(new CatalogCommand(CatalogCommand.Verb.VALIDATE, null, false)))
                .isZero();
        assertThat(printed).anySatisfy(line -> assertThat(line).contains("no problems found"));

        printed.clear();
        assertThat(runner.run(new CatalogCommand(CatalogCommand.Verb.DIFF, null, false))).isZero();
        assertThat(printed).anySatisfy(line -> assertThat(line).contains("+ topic  a  A"));
        assertThat(printed).anySatisfy(line -> assertThat(line).contains("+ edge   SUB:a -> SUB:b"));

        printed.clear();
        assertThat(runner.run(new CatalogCommand(CatalogCommand.Verb.APPLY, null, false))).isZero();
        LOG.info("catalog apply, as an operator sees it:\n    {}", String.join("\n    ", printed));
        assertThat(printed).anySatisfy(line -> assertThat(line).contains("recorded as import"));
        assertThat(topicsOf("SUB")).hasSize(3);

        printed.clear();
        assertThat(runner.run(new CatalogCommand(CatalogCommand.Verb.DIFF, null, false))).isZero();
        assertThat(printed).anySatisfy(line -> assertThat(line).contains("nothing to change"));
    }

    /** A run that does not validate writes nothing and says why. */
    @Test
    void theRunnerRefusesToApplyFilesThatDoNotValidate() {
        write("SUB", """
                code,name,position,effort_tier
                a,A,1,SHORT
                b,B,2,SHORT
                """, """
                prerequisite,dependent,strength,provenance,source_reference
                SUB:a,SUB:b,HARD,CURATED,
                SUB:b,SUB:a,HARD,CURATED,
                """);
        List<String> printed = new ArrayList<>();
        CatalogRunner runner = new CatalogRunner(differ, applier, root, printed::add);

        assertThat(runner.run(new CatalogCommand(CatalogCommand.Verb.APPLY, null, false)))
                .isEqualTo(1);

        assertThat(printed).anySatisfy(line -> assertThat(line).contains("refused"));
        assertThat(printed).anySatisfy(line -> assertThat(line).contains("cycle detected"));
        assertThat(catalog.subjectByCode("SUB")).isEmpty();
    }

    /** Output is for a curator: no class name, no column name, no stack trace. */
    @Test
    void nothingItPrintsNamesAnEntityAColumnOrAStackFrame() {
        threeTopicsAndTwoEdges();
        List<String> printed = new ArrayList<>();
        CatalogRunner runner = new CatalogRunner(differ, applier, root, printed::add);
        runner.run(new CatalogCommand(CatalogCommand.Verb.APPLY, null, false));

        assertThat(printed).allSatisfy(line -> assertThat(line)
                .doesNotContain("br.com.sinapse")
                .doesNotContain("Exception")
                .doesNotContain("topic_prerequisite")
                .doesNotContain("effort_tier"));
    }

    private CatalogApplier.Applied apply(boolean allowTopicRemoval) {
        return applier.apply(read(), GitRevision.of(root),
                allowTopicRemoval);
    }

    private DesiredCatalogue read() {
        return CatalogFiles.read(root, null);
    }

    private List<TopicView> topicsOf(String code) {
        SubjectView subject = catalog.subjectByCode(code).orElseThrow();
        return catalog.topicsOfSubjects(Set.of(subject.id()));
    }

    private List<?> edgesOf(String code) {
        SubjectView subject = catalog.subjectByCode(code).orElseThrow();
        return graph.edgesTouchingSubjects(Set.of(subject.id()));
    }

    private Map<String, Object> importRow(UUID importId) {
        return jdbc.queryForMap("select * from catalog_import where id = ?", importId);
    }

    private int importCount() {
        return jdbc.queryForObject("select count(*) from catalog_import", Integer.class);
    }

    private String topics() {
        return """
                code,name,position,effort_tier
                a,A,1,SHORT
                b,B,2,SHORT
                c,C,3,SHORT
                """;
    }

    private String edges() {
        return """
                prerequisite,dependent,strength,provenance,source_reference
                SUB:a,SUB:b,HARD,CURATED,
                SUB:b,SUB:c,SOFT,TEXTBOOK_ORDER,curricular order of SUB
                """;
    }

    private void threeTopicsAndTwoEdges() {
        write("SUB", topics(), edges());
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
