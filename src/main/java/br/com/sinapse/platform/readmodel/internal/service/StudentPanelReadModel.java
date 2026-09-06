package br.com.sinapse.platform.readmodel.internal.service;

import br.com.sinapse.platform.learningrecord.api.AdherenceReport;
import br.com.sinapse.platform.learningrecord.api.StudyHistory;
import br.com.sinapse.platform.learningrecord.api.StudySessionView;
import br.com.sinapse.platform.learningrecord.api.TopicEffort;
import br.com.sinapse.platform.learningrecord.api.TopicRecallTrajectory;
import br.com.sinapse.platform.planning.api.PlannedSessionView;
import br.com.sinapse.platform.planning.api.PlanningDirectory;
import br.com.sinapse.platform.readmodel.api.PlanSummaryView;
import br.com.sinapse.platform.readmodel.api.StudentPanelView;
import br.com.sinapse.platform.readmodel.internal.config.ReadModelProperties;
import br.com.sinapse.platform.readmodel.internal.error.NotReadableException;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code PainelDoAluno}: what a teacher may see of one student over a window.
 *
 * <p>Section 3.5 of the API contract. Two gates, both at the entry: the classroom has to be the
 * caller's, and {@code TeacherAccessPolicy.canViewStudent} has to hold at this instant — an
 * active enrollment <em>and</em> a valid sharing consent. Neither is cached and nothing
 * propagates, so a student who withdraws disappears from this endpoint on the teacher's very
 * next request and comes back if they consent again, with their enrollment untouched
 * throughout.
 *
 * <p>The scope is the student's whole record over the window and not the part belonging to this
 * teacher's subjects, by decision P1. ADR 0005 took that on interpretability grounds and
 * recorded the cost in data minimisation as accepted rather than absent.
 */
@Service
public class StudentPanelReadModel {

    private final StudyHistory history;
    private final PlanningDirectory planning;
    private final CatalogNames names;
    private final ReadModelAccess access;
    private final ReadWindow window;
    private final Clock clock;
    private final int maxRecentSessions;

    /**
     * @param history    effort, trajectories and sessions
     * @param planning   the sessions that were scheduled, for the adherence denominator
     * @param names      topic and subject names, in two queries
     * @param access     the single gate these reads consult
     * @param window     the ceiling on what may be asked for
     * @param clock      application clock, read to decide what has fallen due
     * @param properties configured ceilings, for the cap on the session list
     */
    public StudentPanelReadModel(StudyHistory history, PlanningDirectory planning,
            CatalogNames names, ReadModelAccess access, ReadWindow window, Clock clock,
            ReadModelProperties properties) {
        this.history = history;
        this.planning = planning;
        this.names = names;
        this.access = access;
        this.window = window;
        this.clock = clock;
        this.maxRecentSessions = properties.maxRecentSessions();
    }

    /**
     * One student's panel.
     *
     * @param teacherAccountId the caller
     * @param classroomId      classroom the caller owns
     * @param studentAccountId student being read
     * @param from             start of the window, inclusive
     * @param to               end of the window, exclusive
     * @return the panel
     * @throws NotReadableException if the classroom is not the caller's, or the student is not
     *                              currently readable by them
     */
    @Transactional(readOnly = true)
    public StudentPanelView of(UUID teacherAccountId, UUID classroomId, UUID studentAccountId,
            Instant from, Instant to) {

        window.require(from, to);
        access.requireOwnedClassroom(teacherAccountId, classroomId);
        access.requireViewableStudent(teacherAccountId, studentAccountId);

        List<TopicEffort> effort = history.effortByTopicOf(studentAccountId, from, to);
        List<TopicRecallTrajectory> trajectories =
                history.recallTrajectoriesOf(studentAccountId, from, to);
        List<StudySessionView> sessions = history.sessionsOf(studentAccountId, from, to);

        Set<UUID> topicIds = new LinkedHashSet<>();
        effort.forEach(entry -> topicIds.add(entry.topicId()));
        trajectories.forEach(entry -> topicIds.add(entry.topicId()));
        sessions.forEach(session -> topicIds.add(session.topicId()));
        CatalogNames.Resolved resolved = names.of(topicIds);

        return new StudentPanelView(
                studentAccountId,
                from,
                to,
                adherence(studentAccountId, from, to),
                minutesBySubject(effort, resolved),
                trajectories(trajectories, resolved),
                recentSessions(sessions, resolved));
    }

    /**
     * How much of what fell due inside the window was carried out.
     *
     * <p>The denominator is the student's own scheduled sessions within the window whose end has
     * already passed. Sessions still to come are not counted, for the reason
     * {@code PlanSummaryReadModel} gives: a plan cannot be behind on a session that has not
     * happened yet.
     */
    private PlanSummaryView.Adherence adherence(UUID studentAccountId, Instant from, Instant to) {

        Instant now = clock.instant();
        List<UUID> due = planning.plannedSessionsOf(studentAccountId, from, to).stream()
                .filter(session -> !session.scheduledEnd().isAfter(now))
                .map(PlannedSessionView::id)
                .toList();
        AdherenceReport report = history.adherenceOf(studentAccountId, due);
        return PlanSummaryReadModel.adherenceOf(report);
    }

    /**
     * Effective time rolled up from topics to subjects.
     *
     * <p>Rolled up here rather than asked for by subject: the learning record holds topic
     * identifiers and does not know what a subject is, and teaching it would be that module
     * reading the catalogue.
     */
    private static List<StudentPanelView.SubjectMinutes> minutesBySubject(List<TopicEffort> effort,
            CatalogNames.Resolved resolved) {

        Map<UUID, long[]> totals = new LinkedHashMap<>();
        for (TopicEffort entry : effort) {
            UUID subjectId = resolved.subjectIdOf(entry.topicId());
            long[] total = totals.computeIfAbsent(subjectId, subject -> new long[2]);
            total[0] += entry.effort().toMinutes();
            total[1] += entry.sessionCount();
        }
        return totals.entrySet().stream()
                .map(entry -> new StudentPanelView.SubjectMinutes(entry.getKey(),
                        entry.getKey() == null ? null : resolved.subjectName(entry.getKey()),
                        entry.getValue()[0], Math.toIntExact(entry.getValue()[1])))
                .sorted(Comparator.comparingLong(StudentPanelView.SubjectMinutes::minutes).reversed())
                .toList();
    }

    private static List<StudentPanelView.TopicTrajectory> trajectories(
            List<TopicRecallTrajectory> trajectories, CatalogNames.Resolved resolved) {

        return trajectories.stream()
                .map(trajectory -> new StudentPanelView.TopicTrajectory(
                        trajectory.topicId(),
                        resolved.topicName(trajectory.topicId()),
                        trajectory.points().stream()
                                .map(point -> new StudentPanelView.TopicTrajectory.Point(
                                        point.at(), point.rating()))
                                .toList()))
                .toList();
    }

    private List<StudentPanelView.SessionSummary> recentSessions(List<StudySessionView> sessions,
            CatalogNames.Resolved resolved) {

        return sessions.stream()
                .limit(maxRecentSessions)
                .map(session -> new StudentPanelView.SessionSummary(
                        session.id(),
                        session.topicId(),
                        resolved.topicName(session.topicId()),
                        resolved.subjectIdOf(session.topicId()),
                        resolved.subjectNameOf(session.topicId()),
                        session.kind(),
                        session.source(),
                        session.status(),
                        session.startedAt(),
                        session.actualDurationMinutes(),
                        session.durationSource(),
                        session.recallRating()))
                .collect(Collectors.toList());
    }
}
