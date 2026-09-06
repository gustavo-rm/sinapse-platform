package br.com.sinapse.platform.datarights.api;

import java.time.Instant;
import java.util.UUID;

/**
 * One period in which a teacher could read the holder's data.
 *
 * <p>The Article 18 right to information about sharing, answered from the only thing that
 * grants that access: an enrollment. There is no direct teacher-to-student relation in this
 * platform (rule R6), so a period is a membership — it opened when the student joined a
 * classroom and closed when the membership ended, or is still open.
 *
 * <p><strong>It is not the whole truth and says so.</strong> Access also requires a valid
 * {@code INSTITUTION_SHARING} consent at the moment of each read, and a withdrawal cuts it off
 * without touching the enrollment. So a period here is an upper bound: the teacher could see
 * the holder's data during it, unless the holder had withdrawn that consent. Narrowing it
 * further would mean logging every read, which is a different and much heavier decision.
 *
 * @param classroomId   classroom the membership was in
 * @param classroomName its name
 * @param teacherName   the teacher who owned it
 * @param institution   the institution they named, or {@code null}
 * @param from          when the membership began
 * @param to            when it ended, or {@code null} while it is still open
 */
public record TeacherAccessPeriod(
        UUID classroomId,
        String classroomName,
        String teacherName,
        String institution,
        Instant from,
        Instant to) {

    /** Whether the teacher can still read the holder's data through this membership. */
    public boolean isOpen() {
        return to == null;
    }
}
