package br.com.sinapse.platform.curriculum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.sinapse.platform.curriculum.api.CatalogCuration;
import br.com.sinapse.platform.curriculum.api.CurriculumCatalog;
import br.com.sinapse.platform.curriculum.api.SubjectView;
import br.com.sinapse.platform.curriculum.api.TopicView;
import br.com.sinapse.platform.curriculum.support.CurriculumIntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Reordering a subject, which is what the deferred constraint on position exists for.
 *
 * <p>A swap has no order of two updates that is valid at every step. If the unique constraint
 * were checked per statement, the only way to reorder anything would be to move rows out of
 * the way through positions nobody asked for — which is the sort of workaround that survives
 * until the day two of them collide.
 */
class TopicReorderingIntegrationTest extends CurriculumIntegrationTest {

    @Autowired
    private CurriculumCatalog catalog;

    @Test
    void reversingAWholeSubjectWorksInOneTransaction() {
        SubjectView subject = subject("Reversible");
        TopicView a = topic(subject, "a", 1);
        TopicView b = topic(subject, "b", 2);
        TopicView c = topic(subject, "c", 3);

        curation.reorderTopics(subject.id(), List.of(
                new CatalogCuration.TopicPosition(a.id(), 3),
                new CatalogCuration.TopicPosition(b.id(), 2),
                new CatalogCuration.TopicPosition(c.id(), 1)));

        assertThat(catalog.topicsOfSubjects(List.of(subject.id())))
                .as("every intermediate state of this violates the unique constraint, and the "
                        + "deferral is what lets it through")
                .extracting(TopicView::id)
                .containsExactly(c.id(), b.id(), a.id());
    }

    @Test
    void swappingTwoAdjacentTopicsWorks() {
        SubjectView subject = subject("Swap");
        TopicView a = topic(subject, "a", 1);
        TopicView b = topic(subject, "b", 2);

        curation.reorderTopics(subject.id(), List.of(
                new CatalogCuration.TopicPosition(a.id(), 2),
                new CatalogCuration.TopicPosition(b.id(), 1)));

        assertThat(catalog.topicsOfSubjects(List.of(subject.id())))
                .extracting(TopicView::code)
                .containsExactly("b", "a");
    }

    @Test
    void aReorderMayLeaveGapsInThePositions() {
        SubjectView subject = subject("Gapped");
        TopicView a = topic(subject, "a", 1);
        TopicView b = topic(subject, "b", 2);

        curation.reorderTopics(subject.id(), List.of(
                new CatalogCuration.TopicPosition(a.id(), 10),
                new CatalogCuration.TopicPosition(b.id(), 20)));

        assertThat(catalog.topicsOfSubjects(List.of(subject.id())))
                .as("position is an ordering and not an index; leaving room between topics is "
                        + "what makes the next insertion cheap")
                .extracting(TopicView::position)
                .containsExactly(10, 20);
    }

    @Test
    void aNegativePositionIsRefused() {
        SubjectView subject = subject("Negative");
        TopicView a = topic(subject, "a", 1);

        assertThatThrownBy(() -> curation.reorderTopics(subject.id(),
                List.of(new CatalogCuration.TopicPosition(a.id(), -1))))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void reorderingASubjectWithNoTopicsIsRefused() {
        SubjectView subject = subject("Empty");

        assertThatThrownBy(() -> curation.reorderTopics(subject.id(), List.of()))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void twoTopicsOfTheSameSubjectStillCannotShareAPositionAtCommit() {
        SubjectView subject = subject("Constraint still there");
        TopicView a = topic(subject, "a", 1);
        topic(subject, "b", 2);

        assertThatThrownBy(() -> jdbc.update("update topic set position = 2 where id = ?", a.id()))
                .as("deferred means checked at commit, not abandoned. A statement outside a "
                        + "transaction commits immediately, so the check runs immediately too.")
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_topic_position");
    }

    @Test
    void twoSubjectsMayUseTheSamePositions() {
        SubjectView first = subject("First");
        SubjectView second = subject("Second");
        UUID firstTopic = topic(first, "a", 1).id();
        UUID secondTopic = topic(second, "a", 1).id();

        assertThat(catalog.topicsByIds(List.of(firstTopic, secondTopic)))
                .as("position orders a subject, not the catalogue")
                .hasSize(2);
    }
}
