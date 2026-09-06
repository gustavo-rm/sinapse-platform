package br.com.sinapse.platform.educational.api;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Whether a teacher may read a student's data.
 *
 * <p>Section 4.3 of the architecture document: there is no direct teacher-to-student
 * relation, and this is the only thing that stands in for one. Access is derived, and it is
 * derived from two facts that are both checked at the moment of the question:
 *
 * <ul>
 *   <li>an active enrollment of that student in a classroom that teacher owns;</li>
 *   <li>a valid {@code INSTITUTION_SHARING} consent from that student.</li>
 * </ul>
 *
 * <p><strong>Both are read at query time, and no event propagates.</strong> ADR 0005
 * considered publishing a revocation event from identity and ending the affected enrollments,
 * and rejected it: it would make educational depend on identity in the wrong direction, and it
 * would behave worse, because a student who re-consented would find their enrollment gone.
 * Checking synchronously means a withdrawal cuts the teacher off on the next request and
 * re-consenting restores them, with nothing to reconcile.
 *
 * <p>The consequence is that this runs on every teacher read, which is why the partial index
 * on active enrollments exists.
 */
public interface TeacherAccessPolicy {

    /**
     * Whether the teacher may read anything at all about the student.
     *
     * @param teacherAccountId account of the teacher asking
     * @param studentAccountId account of the student being asked about
     * @return {@code true} only when both an active enrollment and a valid sharing consent
     *         exist right now
     */
    boolean canViewStudent(UUID teacherAccountId, UUID studentAccountId);

    /**
     * What the teacher may see of the student.
     *
     * <p>Answers {@link VisibilityScope#ALL} whenever {@link #canViewStudent} is true. A
     * caller must still ask {@code canViewStudent} first: a scope is what to show once it is
     * settled that anything may be shown, not the decision itself.
     *
     * @param teacherAccountId account of the teacher asking
     * @param studentAccountId account of the student being asked about
     * @return the scope, or {@link VisibilityScope.Subjects} over an empty set when the
     *         teacher may see nothing
     */
    VisibilityScope scopeFor(UUID teacherAccountId, UUID studentAccountId);

    /**
     * Which of several students the teacher may read.
     *
     * <p>The same two conditions as {@link #canViewStudent}, asked about a whole classroom at
     * once. Rule R7: a class list of forty students asked one at a time is eighty queries for
     * one screen, and the class list is the heaviest read in this system before it has done
     * anything useful.
     *
     * <p>This answers exactly the subset {@link #canViewStudent} would answer {@code true}
     * for. A student the teacher may not read is absent from the result rather than present
     * with something blanked out — the caller has nothing to render for them, which is what
     * makes a withdrawal look like a disappearance instead of a redaction.
     *
     * @param teacherAccountId account of the teacher asking
     * @param studentAccountIds accounts being asked about
     * @return the subset the teacher may read right now
     */
    Set<UUID> viewableStudents(UUID teacherAccountId, Collection<UUID> studentAccountIds);
}
