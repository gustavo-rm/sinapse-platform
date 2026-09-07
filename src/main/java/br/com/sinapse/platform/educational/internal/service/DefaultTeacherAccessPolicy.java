package br.com.sinapse.platform.educational.internal.service;

import br.com.sinapse.platform.educational.api.TeacherAccessPolicy;
import br.com.sinapse.platform.educational.api.VisibilityScope;
import br.com.sinapse.platform.educational.internal.persistence.EnrollmentRepository;
import br.com.sinapse.platform.identity.api.AccountAccessPolicy;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The derived authorisation of ADR 0005.
 *
 * <p>Two conditions, both read now, neither cached:
 *
 * <ol>
 *   <li>an active enrollment of the student in a classroom this teacher owns;</li>
 *   <li>a valid {@code INSTITUTION_SHARING} consent from the student.</li>
 * </ol>
 *
 * <p>The second is one call into {@code identity.api}. This module never reads an identity
 * table, never learns what a consent record is, and never subscribes to anything. Propagating
 * a revocation event was considered and rejected: it would invert the dependency direction,
 * and it would behave worse, because a student who re-consented would find their enrollment
 * already ended and nothing to bring it back.
 *
 * <p>The order matters for cost. The enrollment check is an existence query over a partial
 * index; the consent check reads two rows. Asking the cheap one first means the common case of
 * a teacher who simply has no relation to a student stops immediately.
 */
@Service
public class DefaultTeacherAccessPolicy implements TeacherAccessPolicy {

    private final EnrollmentRepository enrollments;
    private final AccountAccessPolicy accounts;

    /**
     * @param enrollments enrollments
     * @param accounts    the access gate of the identity module
     */
    public DefaultTeacherAccessPolicy(EnrollmentRepository enrollments, AccountAccessPolicy accounts) {
        this.enrollments = enrollments;
        this.accounts = accounts;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canViewStudent(UUID teacherAccountId, UUID studentAccountId) {
        if (teacherAccountId == null || studentAccountId == null) {
            return false;
        }
        return enrollments.existsActiveEnrollmentUnderTeacher(teacherAccountId, studentAccountId)
                && accounts.canShareWithInstitution(studentAccountId);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Two calls for any number of students, in the same order and for the same reason as
     * the unit case: the enrollment query is an index scan that usually eliminates most of the
     * set, and only what survives it is worth asking identity about.
     */
    @Override
    @Transactional(readOnly = true)
    public Set<UUID> viewableStudents(UUID teacherAccountId, Collection<UUID> studentAccountIds) {
        if (teacherAccountId == null || studentAccountIds == null || studentAccountIds.isEmpty()) {
            return Set.of();
        }
        Set<UUID> enrolled = enrollments
                .findStudentsEnrolledUnderTeacher(teacherAccountId, studentAccountIds);
        if (enrolled.isEmpty()) {
            return Set.of();
        }
        Set<UUID> viewable = new LinkedHashSet<>(accounts.canShareWithInstitution(enrolled));
        viewable.retainAll(enrolled);
        return viewable;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Integral scope, per decision P1, and the empty set when the teacher may see nothing.
     * Answering {@code ALL} to a teacher who is not allowed to look would make the scope the
     * decision rather than its consequence.
     */
    @Override
    @Transactional(readOnly = true)
    public VisibilityScope scopeFor(UUID teacherAccountId, UUID studentAccountId) {
        return canViewStudent(teacherAccountId, studentAccountId)
                ? VisibilityScope.ALL
                : new VisibilityScope.Subjects(Set.of());
    }
}
