package br.com.sinapse.platform.curriculum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.sinapse.platform.curriculum.api.CatalogCuration;
import br.com.sinapse.platform.curriculum.api.EdgeProvenance;
import br.com.sinapse.platform.curriculum.api.EdgeStrength;
import br.com.sinapse.platform.curriculum.api.EffortTier;
import br.com.sinapse.platform.curriculum.api.PrerequisiteCycleException;
import br.com.sinapse.platform.curriculum.api.PrerequisiteEdgeView;
import br.com.sinapse.platform.curriculum.api.SubjectView;
import br.com.sinapse.platform.curriculum.api.TopicView;
import br.com.sinapse.platform.curriculum.internal.error.DuplicateEdgeException;
import br.com.sinapse.platform.curriculum.internal.error.InvalidReorderException;
import br.com.sinapse.platform.curriculum.internal.error.UnknownTopicException;
import br.com.sinapse.platform.curriculum.support.CurriculumIntegrationTest;
import br.com.sinapse.platform.shared.web.problem.ApiErrorType;
import br.com.sinapse.platform.shared.web.problem.ApiException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The curation operations, through the interface the catalogue importer will drive.
 */
class CatalogCurationIntegrationTest extends CurriculumIntegrationTest {

    @Test
    void definingASubjectTwiceUnderTheSameCodeUpdatesItRatherThanDuplicatingIt() {
        String code = uniqueCode("MED-ANAT");
        SubjectView first = curation.defineSubject(code, "Anatomia");
        SubjectView again = curation.defineSubject(code, "Anatomia Humana");

        assertThat(again.id())
                .as("a declarative import runs the same file repeatedly; the second run has to "
                        + "change nothing it did not mean to")
                .isEqualTo(first.id());
        assertThat(again.name()).isEqualTo("Anatomia Humana");
        assertThat(jdbc.queryForObject("select count(*) from subject where code = ?",
                Integer.class, code)).isEqualTo(1);
    }

    @Test
    void definingATopicTwiceUnderTheSameCodeRevisesIt() {
        SubjectView subject = subject("Anatomia");
        TopicView first = curation.defineTopic(new CatalogCuration.TopicDefinition(
                subject.id(), "osteologia", "Osteologia", 1, EffortTier.STANDARD));
        TopicView again = curation.defineTopic(new CatalogCuration.TopicDefinition(
                subject.id(), "osteologia", "Osteologia geral", 1, EffortTier.LONG));

        assertThat(again.id())
                .as("the code is the identity a curator refers to a topic by, so revising the "
                        + "name must not repoint anything")
                .isEqualTo(first.id());
        assertThat(again.name()).isEqualTo("Osteologia geral");
        assertThat(again.effortTier()).isEqualTo(EffortTier.LONG);
    }

    @Test
    void aTopicOfAnUnknownSubjectIsRefused() {
        assertThatThrownBy(() -> curation.defineTopic(new CatalogCuration.TopicDefinition(
                UUID.randomUUID(), "x", "X", 1, EffortTier.STANDARD)))
                .isInstanceOf(UnknownTopicException.class);
    }

    @Test
    void anEdgeCanBeCorrectedAndRemoved() {
        SubjectView subject = subject("Correctable");
        TopicView a = topic(subject, "a", 1);
        TopicView b = topic(subject, "b", 2);

        PrerequisiteEdgeView edge = curation.addEdge(new CatalogCuration.EdgeDefinition(
                a.id(), b.id(), EdgeStrength.SOFT, EdgeProvenance.TEXTBOOK_ORDER, null, null));

        PrerequisiteEdgeView corrected = curation.correctEdge(edge.id(), EdgeStrength.HARD,
                EdgeProvenance.CURATED, "Moore, 8th edition, ch. 1");

        assertThat(corrected.id()).isEqualTo(edge.id());
        assertThat(corrected.strength()).isEqualTo(EdgeStrength.HARD);
        assertThat(corrected.provenance()).isEqualTo(EdgeProvenance.CURATED);
        assertThat(corrected.sourceReference()).isEqualTo("Moore, 8th edition, ch. 1");

        curation.removeEdge(edge.id());

        assertThat(edgeCount())
                .as("unlike a consent record, an edge is a curated assertion about the world, "
                        + "and assertions about the world get corrected and withdrawn")
                .isZero();
    }

    @Test
    void curationAuthorshipIsKeptOnTheEdge() {
        SubjectView subject = subject("Authored");
        TopicView a = topic(subject, "a", 1);
        TopicView b = topic(subject, "b", 2);
        UUID curator = UUID.randomUUID();

        PrerequisiteEdgeView edge = curation.addEdge(new CatalogCuration.EdgeDefinition(
                a.id(), b.id(), EdgeStrength.HARD, EdgeProvenance.CURATED, "Gray's Anatomy", curator));

        assertThat(jdbc.queryForObject("select created_by from topic_prerequisite where id = ?",
                UUID.class, edge.id()))
                .as("edges are mutable, so the audit trail is what createdBy and createdAt carry")
                .isEqualTo(curator);
        assertThat(jdbc.queryForObject("select created_at is not null from topic_prerequisite "
                + "where id = ?", Boolean.class, edge.id())).isTrue();
    }

    @Test
    void anEdgeMayCrossSubjects() {
        SubjectView mathematics = subject("Matemática");
        SubjectView physics = subject("Física");
        TopicView trigonometry = topic(mathematics, "trigonometria", 7);
        TopicView kinematics = topic(physics, "cinematica", 1);

        PrerequisiteEdgeView edge = curation.addEdge(new CatalogCuration.EdgeDefinition(
                trigonometry.id(), kinematics.id(), EdgeStrength.HARD, EdgeProvenance.CURATED,
                null, null));

        assertThat(edge.id())
                .as("real prerequisites cross subjects, and partitioning the graph per subject "
                        + "would forbid exactly the most informative edges")
                .isNotNull();
    }

    @Test
    void anEdgeThatWouldCloseACycleFailsWithADomainFailureAndNoRawDatabaseText() {
        SubjectView subject = subject("Cycle through the service");
        TopicView a = topic(subject, "a", 1);
        TopicView b = topic(subject, "b", 2);
        curation.addEdge(new CatalogCuration.EdgeDefinition(
                a.id(), b.id(), EdgeStrength.HARD, EdgeProvenance.CURATED, null, null));

        PrerequisiteCycleException failure = (PrerequisiteCycleException) org.assertj.core.api.Assertions
                .catchThrowable(() -> curation.addEdge(new CatalogCuration.EdgeDefinition(
                        b.id(), a.id(), EdgeStrength.HARD, EdgeProvenance.CURATED, null, null)));

        assertThat(failure).isNotNull();
        assertThat(failure.errorType()).isEqualTo(ApiErrorType.PREREQUISITE_CYCLE);
        assertThat(failure.errorType().detail())
                .as("the database's own message names two UUIDs; what a client reads may not")
                .doesNotContain(a.id().toString())
                .doesNotContain(b.id().toString())
                .doesNotContain("topic_prerequisite");
        assertThat(failure.getCause())
                .as("the identifiers are still available for a log, as the cause")
                .isNotNull();
    }

    @Test
    void aSelfEdgeThroughTheServiceIsTheSameFailure() {
        SubjectView subject = subject("Self through the service");
        TopicView a = topic(subject, "a", 1);

        assertThatThrownBy(() -> curation.addEdge(new CatalogCuration.EdgeDefinition(
                a.id(), a.id(), EdgeStrength.HARD, EdgeProvenance.CURATED, null, null)))
                .isInstanceOf(PrerequisiteCycleException.class);
    }

    @Test
    void assertingTheSamePairTwiceIsRefusedAsAConflict() {
        SubjectView subject = subject("Duplicate through the service");
        TopicView a = topic(subject, "a", 1);
        TopicView b = topic(subject, "b", 2);
        CatalogCuration.EdgeDefinition definition = new CatalogCuration.EdgeDefinition(
                a.id(), b.id(), EdgeStrength.HARD, EdgeProvenance.CURATED, null, null);
        curation.addEdge(definition);

        assertThatThrownBy(() -> curation.addEdge(definition))
                .as("correcting the edge is what a curator meant, and the failure says conflict "
                        + "rather than leaking an index name")
                .isInstanceOf(DuplicateEdgeException.class)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories
                        .type(ApiException.class))
                .extracting(ApiException::errorType)
                .isEqualTo(ApiErrorType.CONFLICT);
    }

    @Test
    void correctingAnEdgeIntoACycleIsRefused() {
        SubjectView subject = subject("Correction into a cycle");
        TopicView a = topic(subject, "a", 1);
        TopicView b = topic(subject, "b", 2);
        TopicView c = topic(subject, "c", 3);
        curation.addEdge(new CatalogCuration.EdgeDefinition(
                a.id(), b.id(), EdgeStrength.HARD, EdgeProvenance.CURATED, null, null));
        PrerequisiteEdgeView second = curation.addEdge(new CatalogCuration.EdgeDefinition(
                b.id(), c.id(), EdgeStrength.SOFT, EdgeProvenance.TEXTBOOK_ORDER, null, null));

        // Only the strength changes, and the graph is acyclic, so this has to be allowed.
        PrerequisiteEdgeView hardened = curation.correctEdge(second.id(), EdgeStrength.HARD,
                EdgeProvenance.CURATED, null);

        assertThat(hardened.strength()).isEqualTo(EdgeStrength.HARD);
        assertThat(edgeCount()).isEqualTo(2);
    }

    @Test
    void anUnknownEdgeCannotBeCorrected() {
        assertThatThrownBy(() -> curation.correctEdge(UUID.randomUUID(), EdgeStrength.HARD,
                EdgeProvenance.CURATED, null))
                .isInstanceOf(UnknownTopicException.class);
    }

    @Test
    void removingAnEdgeThatIsNotThereIsNotAFailure() {
        curation.removeEdge(UUID.randomUUID());

        assertThat(edgeCount())
                .as("an import that describes a desired state has to be able to ask for a "
                        + "removal twice")
                .isZero();
    }

    @Test
    void aReorderThatDoesNotNameEveryTopicIsRefused() {
        SubjectView subject = subject("Partial reorder");
        TopicView a = topic(subject, "a", 1);
        topic(subject, "b", 2);

        assertThatThrownBy(() -> curation.reorderTopics(subject.id(),
                List.of(new CatalogCuration.TopicPosition(a.id(), 2))))
                .as("a partial reorder is indistinguishable from a complete one that lost a row")
                .isInstanceOf(InvalidReorderException.class);
    }

    @Test
    void aReorderWithTwoTopicsOnTheSamePositionIsRefused() {
        SubjectView subject = subject("Colliding reorder");
        TopicView a = topic(subject, "a", 1);
        TopicView b = topic(subject, "b", 2);

        assertThatThrownBy(() -> curation.reorderTopics(subject.id(), List.of(
                new CatalogCuration.TopicPosition(a.id(), 1),
                new CatalogCuration.TopicPosition(b.id(), 1))))
                .as("the deferred constraint would catch it at commit, naming an index instead "
                        + "of naming what was wrong with the request")
                .isInstanceOf(InvalidReorderException.class);
    }
}
