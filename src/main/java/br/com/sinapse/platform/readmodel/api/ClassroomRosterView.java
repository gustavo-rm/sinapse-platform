package br.com.sinapse.platform.readmodel.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/**
 * The students of a classroom, with the two figures a teacher scans a list for.
 *
 * <p>Section 3.6 of the API contract, and three fields by decision F7. It is the heaviest read
 * in this system — it aggregates the history of every student in the classroom — and ADR 0013
 * names it as the first candidate to need materialising. That decision is to come from
 * measurement and not from suspicion, so nothing here is cached, projected or precomputed.
 *
 * <p><strong>A student the teacher may not read is absent, not blanked out.</strong> The gate
 * is {@code TeacherAccessPolicy.viewableStudents}, evaluated at the moment of the read, so a
 * student who withdraws {@code INSTITUTION_SHARING} vanishes from this list on the next
 * request and reappears if they consent again — with their enrollment untouched throughout.
 * Returning them with empty figures would disclose that they are enrolled, which is itself
 * part of what the consent governs.
 *
 * <p><strong>There is no student name</strong>, for the reason given on
 * {@link StudentPanelView}: the contract specifies one and this platform holds none.
 *
 * @param classroomId the classroom
 * @param students    one entry per student the teacher may currently read, in enrollment order
 */
@Schema(description = "The students of a classroom, with adherence and total time")
public record ClassroomRosterView(UUID classroomId, List<Student> students) {

    /** Copies the list, so a roster cannot change after it was produced. */
    public ClassroomRosterView {
        students = List.copyOf(students);
    }

    /**
     * One student of the classroom.
     *
     * @param accountId      the student
     * @param adherenceRatio executed over due within the window, or {@code null} when nothing
     *                       had fallen due. Null rather than zero: a student with no plan yet
     *                       and a student who followed none of theirs are not the same student
     * @param totalMinutes   effective minutes recorded in the window, across every subject
     */
    @Schema(description = "One student's figures for the class list")
    public record Student(UUID accountId, Double adherenceRatio, long totalMinutes) {
    }
}
