package br.com.sinapse.platform.curation;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.sinapse.platform.curation.internal.CatalogApplier;
import br.com.sinapse.platform.curation.internal.CatalogCommand;
import br.com.sinapse.platform.curation.internal.CatalogDiffer;
import br.com.sinapse.platform.curation.internal.CatalogRunner;
import br.com.sinapse.platform.curriculum.api.CurriculumCatalog;
import br.com.sinapse.platform.curriculum.api.EdgeProvenance;
import br.com.sinapse.platform.curriculum.api.PrerequisiteEdgeView;
import br.com.sinapse.platform.curriculum.api.PrerequisiteGraph;
import br.com.sinapse.platform.curriculum.api.SubjectView;
import br.com.sinapse.platform.planning.support.PlanningIntegrationTest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * A catalogue of several subjects against a real database: what the files alone cannot show.
 *
 * <p>Synthetic subjects {@code SYN-A} and {@code SYN-B}, topics {@code T01}…; nothing here
 * resembles a curriculum.
 */
class SeveralSubjectsImportIntegrationTest extends PlanningIntegrationTest {

    private static final String TWO_TOPICS = """
            code,name,position,effort_tier
            T01,Topic 01,1,SHORT
            T02,Topic 02,2,STANDARD
            """;

    private static final String NO_EDGES = "prerequisite,dependent,strength,provenance,source_reference\n";

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

    private final List<String> printed = new ArrayList<>();

    /**
     * {@code SYN-B}'s file declares an edge into {@code SYN-A} and one out of it, so that
     * {@code SYN-A:T02 -> SYN-B:T01 -> SYN-B:T02 -> SYN-A:T01} is in the catalogue once applied.
     */
    @BeforeEach
    void twoSubjectsWhoseEdgesRunThroughEachOther() {
        write("SYN-A", TWO_TOPICS, NO_EDGES);
        write("SYN-B", TWO_TOPICS, """
                prerequisite,dependent,strength,provenance,source_reference
                SYN-A:T02,SYN-B:T01,HARD,PROPOSED,
                SYN-B:T01,SYN-B:T02,HARD,PROPOSED,
                SYN-B:T02,SYN-A:T01,SOFT,PROPOSED,
                """);
        assertThat(runner().run(new CatalogCommand(CatalogCommand.Verb.APPLY, null, false))).isZero();
        printed.clear();
    }

    /**
     * The case most likely to escape: run over one subject, its own new edge is valid in its
     * own file, and the loop closes only through the other subject's edges already stored. The
     * file validator cannot see it; before this check, the database trigger was the first thing
     * to notice, naming one edge by its identifiers.
     */
    @Test
    void aSubjectRunWhoseNewEdgeClosesALoopThroughAnotherSubjectIsRefusedWithThePath() {
        write("SYN-A", TWO_TOPICS, """
                prerequisite,dependent,strength,provenance,source_reference
                SYN-A:T01,SYN-A:T02,HARD,PROPOSED,
                """);
        int edgesBefore = edgesOf("SYN-A").size();

        int exit = runner().run(new CatalogCommand(CatalogCommand.Verb.APPLY, "SYN-A", false));

        assertThat(exit).isEqualTo(1);
        String output = String.join("\n", printed);
        assertThat(output).contains("catalog apply  refused", "cycle detected through the catalogue",
                "SYN-A/prerequisites.csv:2", "SYN-A:T01", "SYN-B:T01", "SYN-B:T02",
                "already in the catalogue that this run does not read",
                "SYN-A:T02 -> SYN-B:T01", "without --subject");
        assertThat(output).doesNotContain("Exception", "topic_prerequisite");
        assertThat(edgesOf("SYN-A")).as("nothing was written").hasSize(edgesBefore);
    }

    @Test
    void theDiffRefusesTheSameLoopBeforeAnyoneTriesToApplyIt() {
        write("SYN-A", TWO_TOPICS, """
                prerequisite,dependent,strength,provenance,source_reference
                SYN-A:T01,SYN-A:T02,HARD,PROPOSED,
                """);

        assertThat(runner().run(new CatalogCommand(CatalogCommand.Verb.DIFF, "SYN-A", false))).isEqualTo(1);
        assertThat(printed).anySatisfy(line -> assertThat(line).contains("cycle detected through the catalogue"));
    }

    @Test
    void runOverBothSubjectsTheSameLoopIsAPlainFileCycle() {
        write("SYN-A", TWO_TOPICS, """
                prerequisite,dependent,strength,provenance,source_reference
                SYN-A:T01,SYN-A:T02,HARD,PROPOSED,
                """);

        assertThat(runner().run(new CatalogCommand(CatalogCommand.Verb.APPLY, null, false))).isEqualTo(1);
        assertThat(String.join("\n", printed)).contains("cycle detected:")
                .doesNotContain("through the catalogue");
    }

    @Test
    void aSubjectRunWhoseNewEdgeClosesNoLoopIsApplied() {
        write("SYN-A", """
                code,name,position,effort_tier
                T01,Topic 01,1,SHORT
                T02,Topic 02,2,STANDARD
                T03,Topic 03,3,LONG
                """, """
                prerequisite,dependent,strength,provenance,source_reference
                SYN-A:T02,SYN-A:T03,HARD,PROPOSED,
                """);

        int edgesBefore = edgesOf("SYN-A").size();

        assertThat(runner().run(new CatalogCommand(CatalogCommand.Verb.APPLY, "SYN-A", false))).isZero();
        assertThat(edgesOf("SYN-A")).hasSize(edgesBefore + 1);
    }

    /**
     * What happened to the example catalogue: every edge relabelled from {@code CURATED} to
     * {@code PROPOSED}. It is an edge correction like any other — recorded, attributable to a
     * revision — and the import row says, in counts, what provenance the applied files carried.
     */
    @Test
    void demotingAnEdgeToProposedIsACorrectionRecordedWithTheProvenanceItLeftBehind() {
        write("SYN-A", TWO_TOPICS, """
                prerequisite,dependent,strength,provenance,source_reference
                SYN-A:T02,SYN-A:T01,HARD,CURATED,Synthetic source 1
                """);
        reviews("SYN-A", "reviewer,reviewed_on\nREV-01,2026-11-01\n");
        assertThat(runner().run(new CatalogCommand(CatalogCommand.Verb.APPLY, "SYN-A", false)))
                .as(String.join("\n", printed)).isZero();
        assertThat(provenanceOfTheEdgeInside("SYN-A")).isEqualTo(EdgeProvenance.CURATED);

        write("SYN-A", TWO_TOPICS, """
                prerequisite,dependent,strength,provenance,source_reference
                SYN-A:T02,SYN-A:T01,HARD,PROPOSED,Synthetic source 1
                """);
        printed.clear();
        assertThat(runner().run(new CatalogCommand(CatalogCommand.Verb.APPLY, "SYN-A", false))).isZero();

        assertThat(provenanceOfTheEdgeInside("SYN-A")).isEqualTo(EdgeProvenance.PROPOSED);
        assertThat(printed).anySatisfy(line -> assertThat(line).contains("~ edge", "CURATED", "PROPOSED"));
        String notes = jdbc.queryForObject(
                "select notes from catalog_import order by applied_at desc limit 1", String.class);
        assertThat(notes).isEqualTo("edges in the files applied, by provenance: PROPOSED 1");
        assertThat(jdbc.queryForObject(
                "select edges_updated from catalog_import order by applied_at desc limit 1", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void theDatabaseRefusesAProvenanceOutsideTheFourValues() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update(
                "update topic_prerequisite set provenance = 'REVIEWED'"))
                .hasMessageContaining("ck_prerequisite_provenance");
    }

    private EdgeProvenance provenanceOfTheEdgeInside(String code) {
        return edgesOf(code).stream()
                .filter(edge -> catalog.topicsByIds(Set.of(edge.prerequisiteTopicId(), edge.dependentTopicId()))
                        .stream().allMatch(topic -> topic.subjectId().equals(subject(code).id())))
                .map(PrerequisiteEdgeView::provenance)
                .findFirst().orElseThrow();
    }

    private List<PrerequisiteEdgeView> edgesOf(String code) {
        return graph.edgesTouchingSubjects(Set.of(subject(code).id()));
    }

    private SubjectView subject(String code) {
        return catalog.subjectByCode(code).orElseThrow();
    }

    private CatalogRunner runner() {
        return new CatalogRunner(differ, applier, root, printed::add);
    }

    private void write(String code, String topics, String prerequisites) {
        try {
            Path directory = Files.createDirectories(root.resolve(code));
            Files.writeString(directory.resolve("topics.csv"), topics, StandardCharsets.UTF_8);
            Files.writeString(directory.resolve("prerequisites.csv"), prerequisites, StandardCharsets.UTF_8);
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
