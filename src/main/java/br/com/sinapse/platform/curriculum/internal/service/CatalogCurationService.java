package br.com.sinapse.platform.curriculum.internal.service;

import br.com.sinapse.platform.curriculum.api.CatalogCuration;
import br.com.sinapse.platform.curriculum.api.EdgeProvenance;
import br.com.sinapse.platform.curriculum.api.EdgeStrength;
import br.com.sinapse.platform.curriculum.api.PrerequisiteEdgeView;
import br.com.sinapse.platform.curriculum.api.TopicStillReferencedException;
import br.com.sinapse.platform.curriculum.api.SubjectView;
import br.com.sinapse.platform.curriculum.api.TopicView;
import br.com.sinapse.platform.curriculum.internal.domain.CatalogImport;
import br.com.sinapse.platform.curriculum.internal.domain.Subject;
import br.com.sinapse.platform.curriculum.internal.domain.Topic;
import br.com.sinapse.platform.curriculum.internal.domain.TopicPrerequisite;
import br.com.sinapse.platform.curriculum.internal.error.InvalidReorderException;
import br.com.sinapse.platform.curriculum.internal.error.UnknownTopicException;
import br.com.sinapse.platform.curriculum.internal.persistence.CatalogImportRepository;
import br.com.sinapse.platform.curriculum.internal.persistence.SubjectRepository;
import br.com.sinapse.platform.curriculum.internal.persistence.TopicPrerequisiteRepository;
import br.com.sinapse.platform.curriculum.internal.persistence.TopicRepository;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The curation operations.
 *
 * <p>Every write to the catalogue goes through here, including the ones the command-line
 * importer makes. The importer could reach the tables directly and deliberately does not: the
 * rules below — idempotence by natural key, a reorder that is all-or-nothing, a seeding that
 * never overwrites a human judgement — are the module's, not the importer's, and a second
 * writer would be a second place to get them wrong.
 *
 * <p>Nothing here validates acyclicity. The database does, with a trigger that takes an
 * advisory lock first, because two concurrent inserts under read committed do not see each
 * other and each would pass a check made here while together closing a cycle (ADR 0006).
 * What this class does is make sure the refusal is <em>seen</em>: every write is flushed
 * inside the call, so the failure surfaces where the caller can be told what happened rather
 * than at commit, in a stack that no longer knows what it was doing.
 */
@Service
public class CatalogCurationService implements CatalogCuration {

    private final SubjectRepository subjects;
    private final TopicRepository topics;
    private final TopicPrerequisiteRepository edges;
    private final CatalogImportRepository imports;
    private final Clock clock;

    /**
     * @param subjects subjects
     * @param topics   topics
     * @param edges    prerequisite edges
     * @param imports  the record of applied catalogue states
     * @param clock    application clock
     */
    public CatalogCurationService(SubjectRepository subjects, TopicRepository topics,
            TopicPrerequisiteRepository edges, CatalogImportRepository imports, Clock clock) {
        this.subjects = subjects;
        this.topics = topics;
        this.edges = edges;
        this.imports = imports;
        this.clock = clock;
    }

    @Override
    @Transactional
    public SubjectView defineSubject(String code, String name) {
        Subject subject = subjects.findByCode(code).orElse(null);
        if (subject == null) {
            subject = subjects.save(new Subject(UUID.randomUUID(), code, name, clock.instant()));
        } else {
            subject.rename(name);
        }
        return CurriculumViews.of(subject);
    }

    @Override
    @Transactional
    public TopicView defineTopic(TopicDefinition definition) {
        if (!subjects.existsById(definition.subjectId())) {
            throw new UnknownTopicException();
        }
        Topic topic = topics.findBySubjectIdAndCode(definition.subjectId(), definition.code())
                .orElse(null);
        if (topic == null) {
            topic = topics.save(new Topic(UUID.randomUUID(), definition.subjectId(), definition.code(),
                    definition.name(), definition.position(), definition.effortTier(), clock.instant()));
        } else {
            topic.reviseTo(definition.name(), definition.position(), definition.effortTier());
        }
        return CurriculumViews.of(topic);
    }

    /**
     * {@inheritDoc}
     *
     * <p>One transaction, and the whole subject at once. The unique constraint on position is
     * declared deferrable and initially deferred, so it is checked when the transaction
     * commits rather than after each statement — which is the only way a swap can work, since
     * there is no order of two updates that keeps a swap valid at every step.
     *
     * <p>The completeness check is what makes the deferred constraint a safety net rather
     * than the error message. Without it a caller that forgot a topic would get a violation of
     * an index at commit, naming a constraint instead of naming what was wrong with the
     * request.
     */
    @Override
    @Transactional
    public void reorderTopics(UUID subjectId, List<TopicPosition> order) {
        Map<UUID, Topic> current = topics.findBySubjectIdOrderByPositionAsc(subjectId).stream()
                .collect(Collectors.toMap(Topic::id, Function.identity()));
        if (current.isEmpty()) {
            throw new UnknownTopicException();
        }

        Set<UUID> named = new HashSet<>();
        Set<Integer> positions = new HashSet<>();
        for (TopicPosition entry : order) {
            if (entry.position() < 0 || !named.add(entry.topicId()) || !positions.add(entry.position())) {
                throw new InvalidReorderException();
            }
        }
        if (!named.equals(current.keySet())) {
            throw new InvalidReorderException();
        }

        order.forEach(entry -> current.get(entry.topicId()).moveTo(entry.position()));
    }

    @Override
    @Transactional
    public PrerequisiteEdgeView addEdge(EdgeDefinition definition) {
        requireTopic(definition.prerequisiteTopicId());
        requireTopic(definition.dependentTopicId());

        TopicPrerequisite edge = new TopicPrerequisite(UUID.randomUUID(),
                definition.prerequisiteTopicId(), definition.dependentTopicId(),
                definition.strength(), definition.provenance(), definition.sourceReference(),
                definition.createdBy(), clock.instant());
        return CurriculumViews.of(write(() -> edges.saveAndFlush(edge)));
    }

    @Override
    @Transactional
    public PrerequisiteEdgeView correctEdge(UUID edgeId, EdgeStrength strength,
            EdgeProvenance provenance, String sourceReference) {

        TopicPrerequisite edge = edges.findById(edgeId).orElseThrow(UnknownTopicException::new);
        edge.correctTo(strength, provenance, sourceReference);
        // Flushed rather than left to the commit so that the trigger, which fires on update as
        // well as on insert, is answered to here. An update that only touches the strength
        // still goes through it; assuming a change is harmless is how a validation stops
        // being applied.
        return CurriculumViews.of(write(() -> edges.saveAndFlush(edge)));
    }

    @Override
    @Transactional
    public void removeEdge(UUID edgeId) {
        edges.findById(edgeId).ifPresent(edges::delete);
    }

    /**
     * {@inheritDoc}
     *
     * <p>A pair that already has an edge is left exactly as it is, whatever its provenance.
     * The rule is usually stated as "never overwrite a curated edge", and this is stricter on
     * purpose: overwriting an earlier seeded edge would be pointless, and overwriting a
     * derived one would silently discard the output of the derivation the ablation is meant to
     * evaluate.
     *
     * <p>If a seeded edge would close a cycle the whole run fails, and that is the right
     * answer rather than a skipped edge: it means the curricular order of the subject
     * contradicts a prerequisite somebody asserted, and a curator has to decide which of the
     * two is wrong.
     */
    @Override
    @Transactional
    public SeedingResult seedTextbookOrder(UUID subjectId, UUID curatedBy) {
        List<Topic> ordered = topics.findBySubjectIdOrderByPositionAsc(subjectId);
        int created = 0;
        int kept = 0;

        for (int i = 0; i + 1 < ordered.size(); i++) {
            UUID prerequisite = ordered.get(i).id();
            UUID dependent = ordered.get(i + 1).id();

            if (edges.findByPrerequisiteTopicIdAndDependentTopicId(prerequisite, dependent).isPresent()) {
                kept++;
                continue;
            }
            TopicPrerequisite edge = new TopicPrerequisite(UUID.randomUUID(), prerequisite, dependent,
                    EdgeStrength.SOFT, EdgeProvenance.TEXTBOOK_ORDER, null, curatedBy, clock.instant());
            write(() -> edges.saveAndFlush(edge));
            created++;
        }
        return new SeedingResult(created, kept);
    }

    private void requireTopic(UUID topicId) {
        if (!topics.existsById(topicId)) {
            throw new UnknownTopicException();
        }
    }

    /**
     * Performs a write to the graph and translates whatever the database says no to.
     */
    private static TopicPrerequisite write(Supplier<TopicPrerequisite> write) {
        try {
            return write.get();
        } catch (RuntimeException failure) {
            throw CycleTranslation.rethrow(failure);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>The edges go first, explicitly, because they are this module's own and an edge to a
     * topic that no longer exists is not something to leave lying about. What is deliberately
     * <em>not</em> done first is any check for references from elsewhere: this module does not
     * know what a study session is (rule R3), so it attempts the delete and reads the answer
     * off the foreign keys.
     */
    @Override
    @Transactional
    public void removeTopic(UUID topicId) {
        Topic topic = topics.findById(topicId).orElseThrow(UnknownTopicException::new);
        edges.deleteAll(edges.findTouching(List.of(topic.id())));
        try {
            topics.delete(topic);
            topics.flush();
        } catch (DataIntegrityViolationException stillReferenced) {
            // A study session or a planned session names it. The whole transaction is going
            // back, which is what makes an import all-or-nothing.
            throw new TopicStillReferencedException(stillReferenced);
        }
    }

    @Override
    @Transactional
    public UUID recordImport(ImportRecord record) {
        CatalogImport applied = imports.save(new CatalogImport(UUID.randomUUID(),
                record.sourceRevision(), clock.instant(),
                new CatalogImport.Counts(record.subjectsAffected(), record.topicsAdded(),
                        record.topicsUpdated(), record.edgesAdded(), record.edgesUpdated(),
                        record.edgesRemoved()),
                record.notes()));
        return applied.id();
    }
}
