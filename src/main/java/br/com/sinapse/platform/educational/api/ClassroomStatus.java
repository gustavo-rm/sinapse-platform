package br.com.sinapse.platform.educational.api;

/**
 * Whether a classroom is still running.
 *
 * <p>Two states and no deletion. Archiving is what ends a classroom, and it ends the
 * enrollments with it — but the enrollments stay, because they are what justifies the access
 * a teacher had to a student's data while the classroom was open.
 */
public enum ClassroomStatus {

    /** Accepting redemptions and running. */
    OPEN,

    /** Closed. Terminal: no invite of an archived classroom is redeemable again. */
    ARCHIVED
}
