package br.com.sinapse.platform.readmodel.api;

import br.com.sinapse.platform.learningrecord.api.DurationSource;
import br.com.sinapse.platform.learningrecord.api.RecallRating;
import br.com.sinapse.platform.learningrecord.api.SessionKind;
import br.com.sinapse.platform.learningrecord.api.SessionSource;
import br.com.sinapse.platform.learningrecord.api.SessionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * What a teacher sees of one student, over a window.
 *
 * <p>Section 3.5 of the API contract. Reachable only through
 * {@code TeacherAccessPolicy.canViewStudent}, which checks an active enrollment
 * <em>and</em> a valid {@code INSTITUTION_SHARING} consent at the moment of the read. A
 * student who withdraws that consent disappears from this endpoint on the teacher's next
 * request, with nothing to propagate and nothing to reconcile.
 *
 * <p>The scope is integral, by decision P1: this is the student's whole record over the
 * window, not the part belonging to the teacher's own subjects. ADR 0005 accepted that cost
 * in data minimisation on interpretability grounds — a plan is allocated across every subject
 * at once, so a teacher who sees only their slice cannot tell why their subject went
 * unstudied — and required in exchange that the student be told, which is what the invite
 * preview does.
 *
 * <p><strong>There is no student name.</strong> Section 3.5 of the contract specifies one and
 * this platform holds none: {@code account} carries an address, a date of birth and a zone,
 * and no name at any point in registration. Inventing a column would be adding a new category
 * of personal data on a read model's initiative, which is a decision for a person and not for
 * this endpoint. The identifier is returned instead, and the gap is reported.
 *
 * @param accountId        the student
 * @param from             start of the window that was answered, inclusive
 * @param to               end of it, exclusive
 * @param adherence        how much of what fell due within the window was done
 * @param minutesBySubject effective time per subject, longest first
 * @param recallTrajectory the student's own recall judgements per topic, oldest first within
 *                         each topic. Not a retention measure: judgement of learning is poorly
 *                         calibrated, and ADR 0008 accepted it as a signal for the optimiser
 *                         rather than as evidence of what was retained
 * @param recentSessions   the sessions of the window, most recent first, capped
 */
@Schema(description = "A teacher's view of one student over a window")
public record StudentPanelView(
        UUID accountId,
        Instant from,
        Instant to,
        PlanSummaryView.Adherence adherence,
        List<SubjectMinutes> minutesBySubject,
        List<TopicTrajectory> recallTrajectory,
        List<SessionSummary> recentSessions) {

    /** Copies every list, so a panel cannot change after it was produced. */
    public StudentPanelView {
        minutesBySubject = List.copyOf(minutesBySubject);
        recallTrajectory = List.copyOf(recallTrajectory);
        recentSessions = List.copyOf(recentSessions);
    }

    /**
     * Effective time on one subject.
     *
     * <p>Only closed sessions with a recorded duration contribute. A session still running has
     * no duration yet, and counting the time elapsed so far would make the total depend on
     * when the teacher happened to look.
     *
     * @param subjectId    subject
     * @param subjectName  its name, from the catalogue
     * @param minutes      total effective minutes
     * @param sessionCount how many closed sessions produced them
     */
    @Schema(description = "Effective time on one subject")
    public record SubjectMinutes(UUID subjectId, String subjectName, long minutes, int sessionCount) {
    }

    /**
     * The recall ratings given for one topic, oldest first.
     *
     * @param topicId   topic
     * @param topicName its name, from the catalogue
     * @param points    the ratings in the order they were given
     */
    @Schema(description = "Recall ratings for one topic over time")
    public record TopicTrajectory(UUID topicId, String topicName, List<Point> points) {

        /** Copies the points. */
        public TopicTrajectory {
            points = List.copyOf(points);
        }

        /**
         * One rating at one instant.
         *
         * @param at     when the session that produced it closed
         * @param rating what the student judged
         */
        @Schema(description = "One recall rating and when it was given")
        public record Point(Instant at, RecallRating rating) {
        }
    }

    /**
     * One executed session, named.
     *
     * @param sessionId             the session
     * @param topicId               topic studied
     * @param topicName             its name, from the catalogue
     * @param subjectId             subject the topic belongs to
     * @param subjectName           its name, from the catalogue
     * @param kind                  new ground or going back over it
     * @param source                from the plan or self-directed
     * @param status                where the session ended up
     * @param startedAt             when it started
     * @param actualDurationMinutes what it took, or {@code null} while it is still running
     * @param durationSource        how the duration was arrived at, present exactly when the
     *                              duration is. Carried because a timed session and one typed
     *                              in afterwards are not equally reliable evidence, and a
     *                              panel that hid the difference would be presenting them as
     *                              if they were
     * @param recallRating          the student's own judgement, only on a completed session
     */
    @Schema(description = "One executed study session")
    public record SessionSummary(
            UUID sessionId,
            UUID topicId,
            String topicName,
            UUID subjectId,
            String subjectName,
            SessionKind kind,
            SessionSource source,
            SessionStatus status,
            Instant startedAt,
            Integer actualDurationMinutes,
            DurationSource durationSource,
            RecallRating recallRating) {
    }
}
