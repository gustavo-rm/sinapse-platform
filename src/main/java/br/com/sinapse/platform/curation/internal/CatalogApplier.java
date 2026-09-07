package br.com.sinapse.platform.curation.internal;

import br.com.sinapse.platform.curation.internal.model.DesiredCatalogue;
import br.com.sinapse.platform.curation.internal.model.DesiredEdge;
import br.com.sinapse.platform.curation.internal.model.DesiredTopic;
import br.com.sinapse.platform.curation.internal.model.TopicKey;
import br.com.sinapse.platform.curriculum.api.CatalogCuration;
import br.com.sinapse.platform.curriculum.api.PrerequisiteEdgeView;
import br.com.sinapse.platform.curriculum.api.SubjectView;
import br.com.sinapse.platform.curriculum.api.TopicView;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies a desired state, in one transaction.
 *
 * <p>One transaction is not a nicety. A partial import leaves the catalogue in a state that no
 * file describes, and the entire argument of ADR 0014 — that a result can be attributed to a
 * revision of the files — collapses the moment that is possible. So the whole run commits or
 * none of it does, including the {@code catalog_import} row that records it.
 *
 * <p><strong>Order matters, and it is the order the constraints require.</strong> Subjects, then
 * topics, then edges removed, then edges added and corrected, and only then topics removed. An
 * edge whose endpoint is about to disappear has to go first; a topic whose position another
 * topic is about to take relies on the deferred unique constraint, which is what lets the
 * positions be written in file order rather than in some safe permutation nobody could derive.
 */
@Component
public class CatalogApplier {

    private final CatalogCuration curation;
    private final CatalogDiffer differ;

    /**
     * @param curation the write side of the catalogue
     * @param differ   what the run would change, recomputed inside the transaction
     */
    public CatalogApplier(CatalogCuration curation, CatalogDiffer differ) {
        this.curation = curation;
        this.differ = differ;
    }

    /**
     * Applies the files.
     *
     * <p>The diff is recomputed here rather than passed in, so that what is applied is what the
     * catalogue looks like inside this transaction and not what it looked like when somebody ran
     * {@code diff} an hour ago.
     *
     * @param desired            what the files describe
     * @param sourceRevision     git revision of those files
     * @param allowTopicRemoval  whether a topic absent from the files may be removed
     * @return what was applied
     */
    @Transactional
    public Applied apply(DesiredCatalogue desired, String sourceRevision, boolean allowTopicRemoval) {
        CatalogDiff diff = differ.diff(desired);

        Map<String, UUID> subjectIds = new LinkedHashMap<>();
        desired.subjects().forEach((code, name) -> {
            SubjectView subject = curation.defineSubject(code, name);
            subjectIds.put(code, subject.id());
        });

        Map<TopicKey, UUID> topicIds = new LinkedHashMap<>();
        diff.stored().forEach((key, topic) -> topicIds.put(key, topic.id()));
        for (DesiredTopic topic : desired.topics()) {
            TopicView written = curation.defineTopic(new CatalogCuration.TopicDefinition(
                    subjectIds.get(topic.key().subjectCode()), topic.key().topicCode(),
                    topic.name(), topic.position(), topic.effortTier()));
            topicIds.put(topic.key(), written.id());
        }

        for (PrerequisiteEdgeView edge : diff.edgesRemoved()) {
            curation.removeEdge(edge.id());
        }
        for (CatalogDiff.EdgeChange change : diff.edgesChanged()) {
            curation.correctEdge(change.stored().id(), change.desired().strength(),
                    change.desired().provenance(), reference(change.desired()));
        }
        for (DesiredEdge edge : diff.edgesAdded()) {
            curation.addEdge(new CatalogCuration.EdgeDefinition(
                    topicIds.get(edge.prerequisite()), topicIds.get(edge.dependent()),
                    edge.strength(), edge.provenance(), reference(edge), null));
        }

        List<TopicView> removed = List.of();
        if (allowTopicRemoval) {
            // Last, and only when asked: every edge that named one of these is already gone,
            // and what is left pointing at them belongs to modules this one cannot see. Their
            // foreign keys are what refuses, and the refusal takes the whole run back.
            diff.topicsMissing().forEach(topic -> curation.removeTopic(topic.id()));
            removed = diff.topicsMissing();
        }

        UUID importId = curation.recordImport(new CatalogCuration.ImportRecord(
                sourceRevision,
                desired.subjects().size(),
                diff.topicsAdded().size(),
                diff.topicsChanged().size(),
                diff.edgesAdded().size(),
                diff.edgesChanged().size(),
                diff.edgesRemoved().size(),
                notes(diff, allowTopicRemoval)));

        return new Applied(importId, diff, removed);
    }

    /**
     * What a curator should be able to read back off the row later.
     *
     * <p>Topics kept despite being absent are the one thing an import does that is invisible in
     * the counts, and it is exactly the thing somebody will want to know about in six months.
     */
    private static String notes(CatalogDiff diff, boolean allowTopicRemoval) {
        if (diff.topicsMissing().isEmpty()) {
            return null;
        }
        return allowTopicRemoval
                ? "%d topic(s) removed on an explicit instruction".formatted(diff.topicsMissing().size())
                : "%d topic(s) absent from the files were kept".formatted(diff.topicsMissing().size());
    }

    private static String reference(DesiredEdge edge) {
        return edge.sourceReference() == null || edge.sourceReference().isBlank()
                ? null
                : edge.sourceReference();
    }

    /**
     * The outcome of one application.
     *
     * @param importId identifier of the recorded import
     * @param diff     what it changed
     * @param removed  topics it actually removed, empty unless removal was asked for
     */
    public record Applied(UUID importId, CatalogDiff diff, List<TopicView> removed) {
    }
}
