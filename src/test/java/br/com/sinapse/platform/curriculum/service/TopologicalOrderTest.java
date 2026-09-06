package br.com.sinapse.platform.curriculum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.sinapse.platform.curriculum.api.EffortTier;
import br.com.sinapse.platform.curriculum.api.PrerequisiteCycleException;
import br.com.sinapse.platform.curriculum.api.TopicView;
import br.com.sinapse.platform.curriculum.internal.service.TopologicalOrder;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The ordering rule, against graphs built by hand.
 *
 * <p>No database here on purpose. The algorithm has to be right for graphs the database will
 * never hold — a cyclic one above all, which the trigger refuses to store and which this
 * still has to fail on cleanly.
 */
class TopologicalOrderTest {

    private static final UUID SUBJECT = UUID.randomUUID();

    @Test
    void aTopicComesAfterItsPrerequisite() {
        TopicView a = topic("a", 1);
        TopicView b = topic("b", 2);

        List<TopicView> ordered = TopologicalOrder.of(List.of(b, a), List.of(edge(a, b)));

        assertThat(ordered).containsExactly(a, b);
    }

    @Test
    void aChainIsOrderedEndToEnd() {
        TopicView a = topic("a", 1);
        TopicView b = topic("b", 2);
        TopicView c = topic("c", 3);

        List<TopicView> ordered = TopologicalOrder.of(List.of(c, a, b),
                List.of(edge(b, c), edge(a, b)));

        assertThat(ordered).containsExactly(a, b, c);
    }

    @Test
    void independentTopicsFollowTheCurricularOrder() {
        TopicView first = topic("z", 1);
        TopicView second = topic("a", 2);
        TopicView third = topic("m", 3);

        List<TopicView> ordered = TopologicalOrder.of(List.of(third, second, first), List.of());

        assertThat(ordered)
                .as("with nothing to decide between them, the curator's own ordering decides")
                .containsExactly(first, second, third);
    }

    @Test
    void theOrderIsTheSameEveryTime() {
        List<TopicView> topics = List.of(topic("a", 1), topic("b", 2), topic("c", 3),
                topic("d", 4), topic("e", 5));

        List<TopicView> once = TopologicalOrder.of(topics, List.of());
        List<TopicView> again = TopologicalOrder.of(topics.reversed(), List.of());

        assertThat(once)
                .as("a plan has to be reproducible from its snapshot, so an ordering that "
                        + "depended on iteration order would make a result unattributable")
                .isEqualTo(again);
    }

    @Test
    void anEdgeWithAnEndpointOutsideTheSetIsIgnored() {
        TopicView inside = topic("a", 5);
        TopicView alsoInside = topic("b", 1);
        TopicView outside = topic("c", 2);

        List<TopicView> ordered = TopologicalOrder.of(List.of(inside, alsoInside),
                List.of(edge(outside, inside)));

        assertThat(ordered)
                .as("a prerequisite that is not being planned says nothing about the order of "
                        + "what is; it constrains when the set may start")
                .containsExactly(alsoInside, inside);
    }

    @Test
    void aCycleIsRefusedRatherThanSilentlyTruncated() {
        TopicView a = topic("a", 1);
        TopicView b = topic("b", 2);
        TopicView c = topic("c", 3);

        assertThatThrownBy(() -> TopologicalOrder.of(List.of(a, b, c),
                List.of(edge(a, b), edge(b, c), edge(c, a))))
                .as("returning the part that could be ordered would hand the core a plan that "
                        + "quietly dropped topics")
                .isInstanceOf(PrerequisiteCycleException.class);
    }

    @Test
    void aTwoTopicCycleIsRefused() {
        TopicView a = topic("a", 1);
        TopicView b = topic("b", 2);

        assertThatThrownBy(() -> TopologicalOrder.of(List.of(a, b), List.of(edge(a, b), edge(b, a))))
                .isInstanceOf(PrerequisiteCycleException.class);
    }

    @Test
    void anEmptySetIsOrderedTrivially() {
        assertThat(TopologicalOrder.of(List.of(), List.of())).isEmpty();
    }

    private static TopicView topic(String code, int position) {
        return new TopicView(UUID.randomUUID(), SUBJECT, code, "Topic " + code, position,
                EffortTier.STANDARD);
    }

    private static TopologicalOrder.Edge edge(TopicView prerequisite, TopicView dependent) {
        return new TopologicalOrder.Edge(prerequisite.id(), dependent.id());
    }
}
