package br.com.sinapse.platform.curriculum.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.sinapse.platform.curriculum.api.CatalogCuration;
import br.com.sinapse.platform.curriculum.api.EdgeProvenance;
import br.com.sinapse.platform.curriculum.api.EdgeStrength;
import br.com.sinapse.platform.curriculum.api.PrerequisiteEdgeView;
import br.com.sinapse.platform.curriculum.api.PrerequisiteGraph;
import br.com.sinapse.platform.curriculum.api.SubjectView;
import br.com.sinapse.platform.curriculum.api.TopicView;
import br.com.sinapse.platform.curriculum.support.CurriculumIntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Seeding a graph from the curricular order.
 *
 * <p>The cheapest way to start a graph, and the second of the three measures ADR 0006 counts
 * on to keep curation affordable: a table of contents is a valid topological order, so assert
 * it and let curation handle the exceptions.
 */
class TextbookOrderSeedingIntegrationTest extends CurriculumIntegrationTest {

    @Autowired
    private PrerequisiteGraph graph;

    @Test
    void seedingLinksConsecutiveTopicsWithSoftEdges() {
        SubjectView subject = subject("Anatomia");
        TopicView first = topic(subject, "osteologia", 1);
        TopicView second = topic(subject, "artrologia", 2);
        TopicView third = topic(subject, "miologia", 3);

        CatalogCuration.SeedingResult result = curation.seedTextbookOrder(subject.id(), null);

        assertThat(result).isEqualTo(new CatalogCuration.SeedingResult(2, 0));

        List<PrerequisiteEdgeView> edges = graph.edgesTouchingSubjects(List.of(subject.id()));
        assertThat(edges).hasSize(2);
        assertThat(edges).allSatisfy(edge -> {
            assertThat(edge.strength())
                    .as("the order of a book is a claim about a sensible sequence, not about a "
                            + "dependency, so it is a penalty and never a constraint")
                    .isEqualTo(EdgeStrength.SOFT);
            assertThat(edge.provenance())
                    .as("provenance is what makes the ablation experiment possible at all")
                    .isEqualTo(EdgeProvenance.TEXTBOOK_ORDER);
        });
        assertThat(edges)
                .extracting(PrerequisiteEdgeView::prerequisiteTopicId,
                        PrerequisiteEdgeView::dependentTopicId)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(first.id(), second.id()),
                        org.assertj.core.groups.Tuple.tuple(second.id(), third.id()));
    }

    @Test
    void seedingTwiceProducesTheSameGraph() {
        SubjectView subject = subject("Idempotent");
        topic(subject, "a", 1);
        topic(subject, "b", 2);
        topic(subject, "c", 3);

        CatalogCuration.SeedingResult first = curation.seedTextbookOrder(subject.id(), null);
        CatalogCuration.SeedingResult second = curation.seedTextbookOrder(subject.id(), null);

        assertThat(first).isEqualTo(new CatalogCuration.SeedingResult(2, 0));
        assertThat(second)
                .as("running it again is a normal thing for an importer to do, and has to be "
                        + "free of effect")
                .isEqualTo(new CatalogCuration.SeedingResult(0, 2));
        assertThat(edgeCount()).isEqualTo(2);
    }

    @Test
    void seedingNeverOverwritesACuratedEdge() {
        SubjectView subject = subject("Curated first");
        TopicView first = topic(subject, "a", 1);
        TopicView second = topic(subject, "b", 2);
        topic(subject, "c", 3);

        PrerequisiteEdgeView curated = curation.addEdge(new CatalogCuration.EdgeDefinition(
                first.id(), second.id(), EdgeStrength.HARD, EdgeProvenance.CURATED,
                "Moore, ch. 1", null));

        CatalogCuration.SeedingResult result = curation.seedTextbookOrder(subject.id(), null);

        assertThat(result).isEqualTo(new CatalogCuration.SeedingResult(1, 1));

        PrerequisiteEdgeView unchanged = graph.edgesTouchingSubjects(List.of(subject.id())).stream()
                .filter(edge -> edge.id().equals(curated.id()))
                .findFirst()
                .orElseThrow();
        assertThat(unchanged.strength())
                .as("a mechanical seeding must never overwrite a human judgement")
                .isEqualTo(EdgeStrength.HARD);
        assertThat(unchanged.provenance()).isEqualTo(EdgeProvenance.CURATED);
        assertThat(unchanged.sourceReference()).isEqualTo("Moore, ch. 1");
    }

    @Test
    void seedingFollowsPositionAndNotTheOrderTopicsWereCreatedIn() {
        SubjectView subject = subject("Out of order");
        TopicView last = topic(subject, "c", 30);
        TopicView middle = topic(subject, "b", 20);
        TopicView first = topic(subject, "a", 10);

        curation.seedTextbookOrder(subject.id(), null);

        assertThat(graph.edgesTouchingSubjects(List.of(subject.id())))
                .extracting(PrerequisiteEdgeView::prerequisiteTopicId,
                        PrerequisiteEdgeView::dependentTopicId)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(first.id(), middle.id()),
                        org.assertj.core.groups.Tuple.tuple(middle.id(), last.id()));
    }

    @Test
    void seedingASubjectWithOneTopicDoesNothing() {
        SubjectView subject = subject("Single");
        topic(subject, "a", 1);

        assertThat(curation.seedTextbookOrder(subject.id(), null))
                .isEqualTo(new CatalogCuration.SeedingResult(0, 0));
        assertThat(edgeCount()).isZero();
    }

    @Test
    void theCuratorRunningTheSeedingIsRecorded() {
        SubjectView subject = subject("Attributed seeding");
        topic(subject, "a", 1);
        topic(subject, "b", 2);
        UUID curator = UUID.randomUUID();

        curation.seedTextbookOrder(subject.id(), curator);

        assertThat(jdbc.queryForObject("select created_by from topic_prerequisite", UUID.class))
                .isEqualTo(curator);
    }
}
