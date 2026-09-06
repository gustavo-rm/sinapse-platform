package br.com.sinapse.platform.learningrecord.api;

import java.util.List;
import java.util.UUID;

/**
 * What this module exposes so that somebody else can check its dangling references.
 *
 * <p>{@code plannedSessionId} carries no foreign key, by rule R2 and section 8.4: neither
 * planning nor the learning record may depend on the other, and that independence is what
 * lets a session recorded off-plan be structurally identical to one recorded from a plan. The
 * accepted cost is that a session can point at a planned session that no longer exists.
 *
 * <p>The check itself cannot live here, because answering "does this planned session exist"
 * requires planning. So this module publishes the references it holds and nothing more; the
 * composition happens above, where knowing both modules is allowed.
 *
 * <p>Deliberately not part of {@link StudyHistory}. Nothing about this is a history read, and
 * putting it there would invite a caller to use it as one.
 */
public interface LearningRecordConsistency {

    /**
     * Every planned session this module holds a reference to.
     *
     * <p>Distinct, and over the whole table rather than over a window: an orphan does not
     * become less orphaned with age, and the point of the check is to find the ones nobody
     * has noticed.
     *
     * @return the identifiers, in no particular order
     */
    List<UUID> referencedPlannedSessionIds();
}
