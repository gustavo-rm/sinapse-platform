package br.com.sinapse.platform.curriculum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.sinapse.platform.curriculum.api.CatalogCuration;
import br.com.sinapse.platform.curriculum.api.CurriculumCatalog;
import br.com.sinapse.platform.curriculum.api.EdgeProvenance;
import br.com.sinapse.platform.curriculum.api.EdgeStrength;
import br.com.sinapse.platform.curriculum.api.EffortTier;
import br.com.sinapse.platform.curriculum.api.EffortTiers;
import br.com.sinapse.platform.curriculum.api.PrerequisiteCycleException;
import br.com.sinapse.platform.curriculum.api.PrerequisiteEdgeView;
import br.com.sinapse.platform.curriculum.api.PrerequisiteGraph;
import br.com.sinapse.platform.curriculum.api.SubjectView;
import br.com.sinapse.platform.curriculum.api.TopicView;
import br.com.sinapse.platform.curriculum.support.CurriculumIntegrationTest;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The queries the orchestration layer will assemble a core snapshot from.
 */
class CurriculumGraphQueryIntegrationTest extends CurriculumIntegrationTest {

    @Autowired
    private PrerequisiteGraph graph;

    @Autowired
    private CurriculumCatalog catalog;

    @Autowired
    private EffortTiers effortTiers;

    @Test
    void theEdgesOfASubjectIncludeTheOnesReachingInFromAnother() {
        SubjectView mathematics = subject("Matemática");
        SubjectView physics = subject("Física");
        TopicView trigonometry = topic(mathematics, "trigonometria", 1);
        TopicView kinematics = topic(physics, "cinematica", 1);
        TopicView dynamics = topic(physics, "dinamica", 2);

        edge(trigonometry, kinematics, EdgeStrength.HARD, EdgeProvenance.CURATED);
        edge(kinematics, dynamics, EdgeStrength.SOFT, EdgeProvenance.TEXTBOOK_ORDER);

        List<PrerequisiteEdgeView> edges = graph.edgesTouchingSubjects(List.of(physics.id()));

        assertThat(edges)
                .as("dropping the cross-subject edge because its other end is elsewhere would "
                        + "silently discard a constraint on planning this subject")
                .hasSize(2);
        assertThat(edges).extracting(PrerequisiteEdgeView::strength)
                .containsExactlyInAnyOrder(EdgeStrength.HARD, EdgeStrength.SOFT);
        assertThat(edges).extracting(PrerequisiteEdgeView::provenance)
                .containsExactlyInAnyOrder(EdgeProvenance.CURATED, EdgeProvenance.TEXTBOOK_ORDER);
    }

    @Test
    void askingAboutNoSubjectsReturnsNothingRatherThanEverything() {
        SubjectView subject = subject("Present");
        TopicView a = topic(subject, "a", 1);
        TopicView b = topic(subject, "b", 2);
        edge(a, b, EdgeStrength.HARD, EdgeProvenance.CURATED);

        assertThat(graph.edgesTouchingSubjects(List.of())).isEmpty();
        assertThat(catalog.topicsOfSubjects(List.of())).isEmpty();
        assertThat(catalog.topicsByIds(List.of())).isEmpty();
        assertThat(graph.topologicalOrder(List.of())).isEmpty();
    }

    @Test
    void theDirectPrerequisitesOfATopicAreItsImmediatePredecessorsOnly() {
        SubjectView subject = subject("Chain");
        TopicView a = topic(subject, "a", 1);
        TopicView b = topic(subject, "b", 2);
        TopicView c = topic(subject, "c", 3);
        edge(a, b, EdgeStrength.HARD, EdgeProvenance.CURATED);
        edge(b, c, EdgeStrength.HARD, EdgeProvenance.CURATED);

        assertThat(graph.directPrerequisitesOf(c.id()))
                .as("direct means one hop; the transitive closure is the caller's to walk")
                .extracting(TopicView::id)
                .containsExactly(b.id());
        assertThat(graph.directPrerequisitesOf(a.id())).isEmpty();
    }

    @Test
    void aTopicWithSeveralPrerequisitesListsThemInCurricularOrder() {
        SubjectView subject = subject("Fan-in");
        TopicView first = topic(subject, "a", 1);
        TopicView second = topic(subject, "b", 2);
        TopicView target = topic(subject, "z", 9);
        edge(second, target, EdgeStrength.HARD, EdgeProvenance.CURATED);
        edge(first, target, EdgeStrength.SOFT, EdgeProvenance.DERIVED);

        assertThat(graph.directPrerequisitesOf(target.id()))
                .extracting(TopicView::code)
                .containsExactly("a", "b");
    }

    @Test
    void anOrderIsProducedForASetSpanningSubjects() {
        SubjectView mathematics = subject("Matemática");
        SubjectView physics = subject("Física");
        TopicView trigonometry = topic(mathematics, "trigonometria", 7);
        TopicView kinematics = topic(physics, "cinematica", 1);
        edge(trigonometry, kinematics, EdgeStrength.HARD, EdgeProvenance.CURATED);

        assertThat(graph.topologicalOrder(List.of(kinematics.id(), trigonometry.id())))
                .as("the curricular position of kinematics is lower, and the edge still wins")
                .extracting(TopicView::id)
                .containsExactly(trigonometry.id(), kinematics.id());
    }

    @Test
    void anOrderIgnoresEdgesLeadingOutOfTheSet() {
        SubjectView subject = subject("Partial");
        TopicView outside = topic(subject, "a", 1);
        TopicView inside = topic(subject, "b", 2);
        TopicView alsoInside = topic(subject, "c", 3);
        edge(outside, inside, EdgeStrength.HARD, EdgeProvenance.CURATED);
        edge(alsoInside, inside, EdgeStrength.HARD, EdgeProvenance.CURATED);

        assertThat(graph.topologicalOrder(List.of(inside.id(), alsoInside.id())))
                .extracting(TopicView::id)
                .containsExactly(alsoInside.id(), inside.id());
    }

    @Test
    void anOrderOfACyclicSetFailsClearly() {
        SubjectView subject = subject("Cyclic set");
        TopicView a = topic(subject, "a", 1);
        TopicView b = topic(subject, "b", 2);
        edge(a, b, EdgeStrength.HARD, EdgeProvenance.CURATED);
        // The database refuses to store the closing edge, so the only way to reach a cyclic
        // set is to put one there behind its back. Doing that is the point: the query has to
        // fail cleanly even in a state the database says cannot exist.
        jdbc.execute("alter table topic_prerequisite disable trigger tg_topic_prerequisite_acyclic");
        try {
            insertEdgeDirectly(b.id(), a.id());
        } finally {
            jdbc.execute("alter table topic_prerequisite enable trigger tg_topic_prerequisite_acyclic");
        }

        assertThatThrownBy(() -> graph.topologicalOrder(List.of(a.id(), b.id())))
                .as("a hang, a loop, or an arbitrary order is what a cycle produces downstream; "
                        + "a named failure is the only acceptable answer")
                .isInstanceOf(PrerequisiteCycleException.class);
    }

    @Test
    void topicsCarryTheirEffortBandAndTheBandHasAConfiguredDuration() {
        SubjectView subject = subject("Effort");
        TopicView topic = curation.defineTopic(new CatalogCuration.TopicDefinition(
                subject.id(), "long-one", "A long topic", 1, EffortTier.EXTENDED));

        assertThat(catalog.topicsByIds(List.of(topic.id())))
                .singleElement()
                .extracting(TopicView::effortTier)
                .isEqualTo(EffortTier.EXTENDED);

        assertThat(effortTiers.mapping())
                .as("every band has to be schedulable, or a topic exists that nothing can size")
                .containsOnlyKeys(EffortTier.values());
        assertThat(effortTiers.plannedDurationOf(EffortTier.EXTENDED))
                .isGreaterThan(effortTiers.plannedDurationOf(EffortTier.SHORT))
                .isGreaterThan(Duration.ZERO);
    }

    @Test
    void aSubjectIsFoundByItsStableCode() {
        SubjectView subject = subject("By code");

        assertThat(catalog.subjectByCode(subject.code()))
                .hasValueSatisfying(found -> assertThat(found.id()).isEqualTo(subject.id()));
        assertThat(catalog.subjectByCode("no-such-code")).isEmpty();
    }

    @Test
    void topicsOfSeveralSubjectsComeBackInOneCall() {
        SubjectView first = subject("First");
        SubjectView second = subject("Second");
        topic(first, "a", 1);
        topic(first, "b", 2);
        topic(second, "c", 1);

        assertThat(catalog.topicsOfSubjects(List.of(first.id(), second.id())))
                .as("rule R7: the snapshot assembly reads several subjects at once, and asking "
                        + "per subject would give it one query per subject")
                .hasSize(3);
    }

    private void edge(TopicView prerequisite, TopicView dependent, EdgeStrength strength,
            EdgeProvenance provenance) {
        curation.addEdge(new CatalogCuration.EdgeDefinition(prerequisite.id(), dependent.id(),
                strength, provenance, null, UUID.randomUUID()));
    }
}
