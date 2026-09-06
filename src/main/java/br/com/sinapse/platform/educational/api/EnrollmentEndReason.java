package br.com.sinapse.platform.educational.api;

/**
 * Why an enrollment ended.
 *
 * <p>An enrollment is never deleted, so this is the other half of the timestamp: the record
 * says both that the student was in the classroom and why they stopped being.
 */
public enum EnrollmentEndReason {

    /** The student left the classroom. */
    STUDENT_LEFT,

    /** The teacher removed the student. */
    TEACHER_REMOVED,

    /** The classroom was archived, which ends every active enrollment in it. */
    CLASSROOM_ARCHIVED,

    /**
     * The student withdrew their sharing consent.
     *
     * <p><strong>Nothing writes this, deliberately.</strong> ADR 0005 rejected propagating a
     * revocation event from identity, for three reasons: it would invert the dependency
     * direction, the synchronous check is simpler with the same effect, and — the one that
     * decides it — ending the enrollment would produce worse behaviour, because re-consenting
     * would not bring it back. So a revocation cuts the teacher's access at the next query and
     * leaves the enrollment alone. The value stays in the schema because a later decision to
     * end enrollments on revocation would need it, and adding it then would be a migration.
     */
    CONSENT_REVOKED
}
