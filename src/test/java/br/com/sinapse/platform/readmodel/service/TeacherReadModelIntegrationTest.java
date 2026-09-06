package br.com.sinapse.platform.readmodel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.sinapse.platform.curriculum.api.TopicView;
import br.com.sinapse.platform.educational.api.ClassroomView;
import br.com.sinapse.platform.identity.internal.domain.Account;
import br.com.sinapse.platform.learningrecord.api.RecallRating;
import br.com.sinapse.platform.readmodel.api.ClassroomRosterView;
import br.com.sinapse.platform.readmodel.api.StudentPanelView;
import br.com.sinapse.platform.readmodel.internal.error.InvalidReadWindowException;
import br.com.sinapse.platform.readmodel.internal.error.NotReadableException;
import br.com.sinapse.platform.readmodel.support.ReadModelIntegrationTest;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** {@code PainelDoAluno} and {@code ListaDaTurma}: what a teacher may see, and for how long. */
class TeacherReadModelIntegrationTest extends ReadModelIntegrationTest {

    @Test
    void aPanelCarriesTheStudentsEffortRecallAndSessions() {
        Account teacher = teacherAccount();
        ClassroomView classroom = classroomOf(teacher);
        Account student = student();
        enrol(student, teacher, classroom);
        TopicView topic = topic();
        studied(student, topic.id(), Duration.ofDays(3), 40, RecallRating.HARD);
        studied(student, topic.id(), Duration.ofDays(1), 50, RecallRating.GOOD);

        StudentPanelView panel = panels.of(teacher.id(), classroom.id(), student.id(),
                windowStart(), windowEnd());

        assertThat(panel.accountId()).isEqualTo(student.id());
        assertThat(panel.minutesBySubject()).singleElement().satisfies(subject -> {
            assertThat(subject.minutes()).isEqualTo(90);
            assertThat(subject.sessionCount()).isEqualTo(2);
            assertThat(subject.subjectName()).isNotBlank();
        });
        assertThat(panel.recallTrajectory()).singleElement().satisfies(trajectory -> {
            assertThat(trajectory.topicName()).isEqualTo(topic.name());
            assertThat(trajectory.points()).extracting(StudentPanelView.TopicTrajectory.Point::rating)
                    .as("oldest first, so a trajectory reads as a sequence over time")
                    .containsExactly(RecallRating.HARD, RecallRating.GOOD);
        });
        assertThat(panel.recentSessions()).hasSize(2);
        assertThat(panel.recentSessions()).allSatisfy(session ->
                assertThat(session.subjectName()).isNotBlank());
    }

    /** The cap of section 1 of the API contract, applied where a list could grow. */
    @Test
    void theSessionListIsCappedRatherThanPaged() {
        Account teacher = teacherAccount();
        ClassroomView classroom = classroomOf(teacher);
        Account student = student();
        enrol(student, teacher, classroom);
        TopicView topic = topic();
        for (int session = 1; session <= 8; session++) {
            studied(student, topic.id(), Duration.ofHours(session), 30, RecallRating.GOOD);
        }

        StudentPanelView panel = panels.of(teacher.id(), classroom.id(), student.id(),
                windowStart(), windowEnd());

        assertThat(panel.recentSessions())
                .as("configured to five in the test profile")
                .hasSize(5);
        assertThat(panel.minutesBySubject().getFirst().sessionCount())
                .as("the totals are over everything, not over what the list happened to show")
                .isEqualTo(8);
    }

    @Test
    void theClassListCarriesAdherenceAndTotalTimePerStudent() {
        Account teacher = teacherAccount();
        ClassroomView classroom = classroomOf(teacher);
        Account first = student();
        Account second = student();
        enrol(first, teacher, classroom);
        enrol(second, teacher, classroom);
        TopicView topic = topic();
        studied(first, topic.id(), Duration.ofDays(2), 60, RecallRating.GOOD);

        ClassroomRosterView roster = rosters.of(teacher.id(), classroom.id(),
                windowStart(), windowEnd());

        assertThat(roster.classroomId()).isEqualTo(classroom.id());
        assertThat(roster.students()).extracting(ClassroomRosterView.Student::accountId)
                .containsExactlyInAnyOrder(first.id(), second.id());
        assertThat(minutesOf(roster, first.id())).isEqualTo(60);
        assertThat(minutesOf(roster, second.id()))
                .as("a student with nothing recorded is present with zero, not absent")
                .isZero();
        assertThat(roster.students()).allSatisfy(entry ->
                assertThat(entry.adherenceRatio())
                        .as("nothing has fallen due, so there is no ratio to state")
                        .isNull());
    }

    /**
     * The sequence the prompt asks for, end to end, with the enrollment untouched throughout.
     *
     * <p>This is the whole of ADR 0005 in one test. Access is derived and read at query time, so
     * a withdrawal cuts the teacher off on the very next request and re-consenting restores them
     * — no event to publish, nothing to reconcile, and no enrollment ended and then unable to
     * come back.
     */
    @Test
    void aWithdrawalCutsTheTeacherOffAtOnceAndConsentingAgainRestoresThem() {
        Account teacher = teacherAccount();
        ClassroomView classroom = classroomOf(teacher);
        Account student = student();
        enrol(student, teacher, classroom);
        studied(student, topic().id(), Duration.ofDays(1), 30, RecallRating.GOOD);
        UUID enrollmentId = activeEnrollmentId(student);

        assertThat(rosters.of(teacher.id(), classroom.id(), windowStart(), windowEnd()).students())
                .hasSize(1);
        assertThat(panels.of(teacher.id(), classroom.id(), student.id(), windowStart(), windowEnd()))
                .isNotNull();

        revokeSharing(student);

        assertThat(rosters.of(teacher.id(), classroom.id(), windowStart(), windowEnd()).students())
                .as("absent rather than blanked out: that they are enrolled is itself covered "
                        + "by the consent")
                .isEmpty();
        assertThatThrownBy(() -> panels.of(teacher.id(), classroom.id(), student.id(),
                        windowStart(), windowEnd()))
                .isInstanceOf(NotReadableException.class);
        assertThat(activeEnrollmentId(student))
                .as("nothing was ended, which is what makes coming back possible")
                .isEqualTo(enrollmentId);

        grantSharing(student);

        assertThat(rosters.of(teacher.id(), classroom.id(), windowStart(), windowEnd()).students())
                .hasSize(1);
        assertThat(panels.of(teacher.id(), classroom.id(), student.id(), windowStart(), windowEnd())
                .minutesBySubject()).isNotEmpty();
        assertThat(activeEnrollmentId(student)).isEqualTo(enrollmentId);
    }

    @Test
    void anotherTeachersClassroomAnswersAsThoughItDidNotExist() {
        Account owner = teacherAccount();
        Account other = teacherAccount();
        ClassroomView classroom = classroomOf(owner);
        Account student = student();
        enrol(student, owner, classroom);

        assertThatThrownBy(() -> rosters.of(other.id(), classroom.id(), windowStart(), windowEnd()))
                .isInstanceOf(NotReadableException.class);
        assertThatThrownBy(() -> panels.of(other.id(), classroom.id(), student.id(),
                        windowStart(), windowEnd()))
                .isInstanceOf(NotReadableException.class);
        assertThatThrownBy(() -> rosters.of(other.id(), UUID.randomUUID(), windowStart(), windowEnd()))
                .as("the same answer for a classroom that does not exist at all")
                .isInstanceOf(NotReadableException.class);
    }

    /** A student of another classroom of the same teacher is not readable through this one. */
    @Test
    void aStudentOfAnotherClassroomIsNotReadableThroughThisOne() {
        Account teacher = teacherAccount();
        ClassroomView first = classroomOf(teacher);
        ClassroomView second = classroomOf(teacher);
        Account student = student();
        enrol(student, teacher, second);

        assertThat(rosters.of(teacher.id(), first.id(), windowStart(), windowEnd()).students())
                .isEmpty();
    }

    @Test
    void bothTeacherReadsRefuseAWindowWiderThanTheServerAnswers() {
        Account teacher = teacherAccount();
        ClassroomView classroom = classroomOf(teacher);
        Account student = student();
        enrol(student, teacher, classroom);
        java.time.Instant from = clock.instant().minus(Duration.ofDays(60));

        assertThatThrownBy(() -> rosters.of(teacher.id(), classroom.id(), from, clock.instant()))
                .isInstanceOf(InvalidReadWindowException.class);
        assertThatThrownBy(() -> panels.of(teacher.id(), classroom.id(), student.id(), from,
                        clock.instant()))
                .isInstanceOf(InvalidReadWindowException.class);
    }

    private static long minutesOf(ClassroomRosterView roster, UUID accountId) {
        return roster.students().stream()
                .filter(entry -> entry.accountId().equals(accountId))
                .map(ClassroomRosterView.Student::totalMinutes)
                .findFirst()
                .orElseThrow();
    }

    private UUID activeEnrollmentId(Account student) {
        List<br.com.sinapse.platform.educational.api.EnrollmentView> active =
                educational.activeEnrollmentsOf(List.of(student.id()));
        return active.isEmpty() ? null : active.getFirst().id();
    }
}
