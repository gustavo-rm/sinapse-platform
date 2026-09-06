package br.com.sinapse.platform.readmodel.internal.service;

import br.com.sinapse.platform.educational.api.EducationalDirectory;
import br.com.sinapse.platform.educational.api.EnrollmentView;
import br.com.sinapse.platform.educational.api.TeacherAccessPolicy;
import br.com.sinapse.platform.learningrecord.api.SessionStatus;
import br.com.sinapse.platform.learningrecord.api.StudyHistory;
import br.com.sinapse.platform.learningrecord.api.StudySessionView;
import br.com.sinapse.platform.planning.api.PlannedSessionView;
import br.com.sinapse.platform.planning.api.PlanningDirectory;
import br.com.sinapse.platform.readmodel.api.ClassroomRosterView;
import br.com.sinapse.platform.readmodel.internal.config.ReadModelProperties;
import br.com.sinapse.platform.readmodel.internal.error.NotReadableException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code ListaDaTurma}: the students of a classroom, with adherence and total time.
 *
 * <p>Section 3.6 of the API contract, three fields by decision F7, and the heaviest read in
 * this system: it aggregates the history of every student in the classroom. ADR 0013 names it
 * as the first candidate to need materialising and requires that the decision come from
 * measurement rather than suspicion, so nothing here is cached or precomputed.
 *
 * <p><strong>Six queries, for a classroom of any size.</strong> The enrollments, then the
 * authorisation in two, then every student's scheduled sessions in one, their executions in
 * one, and their effective time in one. Every one of those is a batch method that exists on
 * some module's {@code api} for this read and no other; without them this method would be four
 * queries per student, and the difference would show up as latency in production and nowhere in
 * the test suite.
 *
 * <p><strong>Adherence is counted here, not asked for.</strong> The learning record does not
 * know what a plan is, so it is given the set of planned sessions that have already fallen due
 * and answers which of them carry a completed execution; attributing those answers back to each
 * student is this class's work, because it is the only party that knows whose session was
 * whose.
 */
@Service
public class ClassroomRosterReadModel {

    private final EducationalDirectory classrooms;
    private final TeacherAccessPolicy teachers;
    private final PlanningDirectory planning;
    private final StudyHistory history;
    private final ReadModelAccess access;
    private final ReadWindow window;
    private final Clock clock;
    private final int maxStudents;

    /**
     * @param classrooms enrollments of the classroom
     * @param teachers   which of those students may be read right now
     * @param planning   every student's scheduled sessions, in one query
     * @param history    their executions and their effective time, in one query each
     * @param access     the single gate these reads consult
     * @param window     the ceiling on what may be asked for
     * @param clock      application clock, read to decide what has fallen due
     * @param properties configured ceilings, for the cap on the list
     */
    public ClassroomRosterReadModel(EducationalDirectory classrooms, TeacherAccessPolicy teachers,
            PlanningDirectory planning, StudyHistory history, ReadModelAccess access,
            ReadWindow window, Clock clock, ReadModelProperties properties) {
        this.classrooms = classrooms;
        this.teachers = teachers;
        this.planning = planning;
        this.history = history;
        this.access = access;
        this.window = window;
        this.clock = clock;
        this.maxStudents = properties.maxClassroomStudents();
    }

    /**
     * The class list of one classroom over a window.
     *
     * @param teacherAccountId the caller
     * @param classroomId      classroom the caller owns
     * @param from             start of the window, inclusive
     * @param to               end of the window, exclusive
     * @return the students the caller may currently read
     * @throws NotReadableException if the classroom is not the caller's
     */
    @Transactional(readOnly = true)
    public ClassroomRosterView of(UUID teacherAccountId, UUID classroomId, Instant from, Instant to) {
        window.require(from, to);
        access.requireOwnedClassroom(teacherAccountId, classroomId);

        List<UUID> enrolled = classrooms.activeEnrollmentsIn(classroomId).stream()
                .map(EnrollmentView::accountId)
                .distinct()
                .limit(maxStudents)
                .toList();
        if (enrolled.isEmpty()) {
            return new ClassroomRosterView(classroomId, List.of());
        }

        Set<UUID> viewable = teachers.viewableStudents(teacherAccountId, enrolled);
        List<UUID> students = enrolled.stream().filter(viewable::contains).toList();
        if (students.isEmpty()) {
            return new ClassroomRosterView(classroomId, List.of());
        }

        Map<UUID, List<UUID>> dueByStudent = dueByStudent(students, from, to);
        Set<UUID> completed = completedAmong(dueByStudent);
        Map<UUID, Duration> effort = history.effortOf(students, from, to);

        return new ClassroomRosterView(classroomId, students.stream()
                .map(student -> student(student, dueByStudent.getOrDefault(student, List.of()),
                        completed, effort))
                .toList());
    }

    /**
     * Each student's scheduled sessions inside the window whose end has already passed.
     *
     * <p>One query for the whole classroom, and the grouping is by the account the plan belongs
     * to — which is why the batch method returns the sessions per account rather than in a flat
     * list the caller would have to attribute.
     */
    private Map<UUID, List<UUID>> dueByStudent(List<UUID> students, Instant from, Instant to) {
        Instant now = clock.instant();
        Map<UUID, List<PlannedSessionView>> planned =
                planning.plannedSessionsOfAccounts(students, from, to);

        Map<UUID, List<UUID>> due = new LinkedHashMap<>();
        planned.forEach((student, sessions) -> {
            List<UUID> ids = new ArrayList<>();
            for (PlannedSessionView session : sessions) {
                if (!session.scheduledEnd().isAfter(now)) {
                    ids.add(session.id());
                }
            }
            if (!ids.isEmpty()) {
                due.put(student, ids);
            }
        });
        return due;
    }

    /**
     * Which of every student's due sessions were completed, in one query for all of them.
     *
     * <p>The alternative the learning record's contract rejects is a batch adherence method of
     * its own: the counts differ per student only in how the identifiers are attributed, and
     * attribution is knowledge this class has and that module does not.
     */
    private Set<UUID> completedAmong(Map<UUID, List<UUID>> dueByStudent) {
        Set<UUID> allDue = new LinkedHashSet<>();
        dueByStudent.values().forEach(allDue::addAll);
        if (allDue.isEmpty()) {
            return Set.of();
        }
        Map<UUID, StudySessionView> executions = history.executionsOf(allDue);
        Set<UUID> completed = new LinkedHashSet<>();
        executions.forEach((plannedSessionId, session) -> {
            if (session.status() == SessionStatus.COMPLETED) {
                completed.add(plannedSessionId);
            }
        });
        return completed;
    }

    private static ClassroomRosterView.Student student(UUID accountId, List<UUID> due,
            Set<UUID> completed, Map<UUID, Duration> effort) {

        long executed = due.stream().filter(completed::contains).count();
        Double ratio = due.isEmpty() ? null : (double) executed / due.size();
        return new ClassroomRosterView.Student(accountId, ratio,
                effort.getOrDefault(accountId, Duration.ZERO).toMinutes());
    }
}
