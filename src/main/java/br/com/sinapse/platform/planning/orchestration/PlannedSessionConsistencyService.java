package br.com.sinapse.platform.planning.orchestration;

import br.com.sinapse.platform.learningrecord.api.LearningRecordConsistency;
import br.com.sinapse.platform.planning.internal.persistence.PlannedSessionRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Finds executed sessions that point at planned sessions which no longer exist.
 *
 * <p><strong>It reports. It does not delete.</strong> An orphan reference is the accepted cost
 * of {@code study_session.planned_session_id} carrying no foreign key, and that cost was
 * accepted for a reason — the missing key is what keeps planning and the learning record
 * independent of each other (rule R2, section 8.4). Deleting the study session to tidy the
 * reference would destroy evidence that a student actually studied in order to remove a
 * dangling pointer, which is a worse outcome than the pointer.
 *
 * <p>It lives here rather than in {@code learningrecord} because answering the question needs
 * both modules, and the learning record may not know what a plan is. Composition across the
 * two is exactly what {@code planning.orchestration} exists for.
 *
 * <p>What reaches the log is the count and nothing else. The identifiers are returned to the
 * caller, so an operator investigating has them and a test can assert on them, but a
 * planned session identifier in a log aggregator is a pointer into a student's record sitting
 * somewhere with weaker access control than the database it came from. Section 9.5 makes that
 * call once, and the count is what an alert actually needs.
 */
@Service
public class PlannedSessionConsistencyService {

    private static final Logger LOG =
            LoggerFactory.getLogger(PlannedSessionConsistencyService.class);

    /** Chunk size for the lookup, so that one enormous statement is never assembled. */
    private static final int BATCH = 1000;

    private final LearningRecordConsistency learningRecord;
    private final PlannedSessionRepository plannedSessions;

    /**
     * @param learningRecord  the references the learning record holds
     * @param plannedSessions which planned sessions exist
     */
    public PlannedSessionConsistencyService(LearningRecordConsistency learningRecord,
            PlannedSessionRepository plannedSessions) {
        this.learningRecord = learningRecord;
        this.plannedSessions = plannedSessions;
    }

    /**
     * Looks for dangling references.
     *
     * @return the planned session identifiers referenced by a study session and absent from
     *         planning, in no particular order. Empty when everything lines up
     */
    @Transactional(readOnly = true)
    public List<UUID> findOrphanReferences() {
        List<UUID> referenced = learningRecord.referencedPlannedSessionIds();
        if (referenced.isEmpty()) {
            return List.of();
        }
        Set<UUID> existing = existingAmong(referenced);
        List<UUID> orphans = referenced.stream()
                .filter(id -> !existing.contains(id))
                .toList();

        if (!orphans.isEmpty()) {
            LOG.warn("Study sessions reference planned sessions that no longer exist: count={}, "
                    + "referenced={}", orphans.size(), referenced.size());
        }
        return orphans;
    }

    /**
     * Which of the given identifiers name a planned session.
     *
     * <p>Asked in chunks. The set is every reference the learning record holds, which grows
     * with the whole pilot, and a single {@code in} list of that size is a statement no
     * database should be handed.
     *
     * @param plannedSessionIds identifiers to look for
     * @return the ones that exist
     */
    private Set<UUID> existingAmong(List<UUID> plannedSessionIds) {
        Set<UUID> existing = new HashSet<>();
        for (int start = 0; start < plannedSessionIds.size(); start += BATCH) {
            int end = Math.min(start + BATCH, plannedSessionIds.size());
            existing.addAll(plannedSessions.findExistingIds(plannedSessionIds.subList(start, end)));
        }
        return existing;
    }
}
