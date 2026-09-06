package br.com.sinapse.platform.educational.api;

import java.util.Set;
import java.util.UUID;

/**
 * What a teacher may see of a student.
 *
 * <p>Today it is always {@link All}. It is modelled as a sealed hierarchy anyway, because it
 * is the single point at which decision P1 reverses. That decision — integral scope, the
 * teacher sees the student's whole history and plan rather than the slice belonging to their
 * own subjects — was taken on interpretability grounds: the plan the engine produces is
 * global, allocating time across every subject at once, and a teacher who sees only their
 * slice cannot tell why the student did not study their subject, because the explanation is
 * usually the allocation made for another one.
 *
 * <p>The cost in data minimisation is real and was accepted knowingly. ADR 0005 records the
 * risk in plain terms: a teacher of one subject sees a student's effort on subjects that are
 * none of their business, including preparation for a competing institution. If the pilot
 * shows that to be a problem, the reversal is a change to this type and to the places that
 * consume it — which is exactly why it is a type and not a boolean.
 */
public sealed interface VisibilityScope permits VisibilityScope.All, VisibilityScope.Subjects {

    /** The single instance of the integral scope. */
    All ALL = new All();

    /** Everything the student has. */
    record All() implements VisibilityScope {
    }

    /**
     * Only the given subjects.
     *
     * <p>Nothing produces this yet. It exists so that producing it later is a change of one
     * expression rather than a sweep across every caller.
     *
     * @param subjectIds subjects the teacher may see
     */
    record Subjects(Set<UUID> subjectIds) implements VisibilityScope {

        /** Copies the set, so a scope cannot widen after it was decided. */
        public Subjects {
            subjectIds = Set.copyOf(subjectIds);
        }
    }
}
