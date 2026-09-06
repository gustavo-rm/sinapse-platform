package br.com.sinapse.platform.planning.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.sinapse.platform.learningrecord.api.DurationSource;
import br.com.sinapse.platform.learningrecord.api.SessionKind;
import br.com.sinapse.platform.learningrecord.api.SessionSource;
import br.com.sinapse.platform.learningrecord.api.SessionStatus;
import br.com.sinapse.platform.learningrecord.api.StudySessionView;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The per-student effort adjustment of decision L1, and the two guards on it.
 *
 * <p>The guards are the interesting part. Neither is in the decision, and both are here because
 * the unguarded ratio behaves badly on the data a pilot actually produces: one short session
 * against a long slot would rebuild every estimate in the plan from a single number.
 */
class EffortCalibrationTest {

    private static final Instant NOW = Instant.parse("2026-09-06T12:00:00Z");

    private static final int MIN_SESSIONS = 5;

    @Test
    void aStudentWithNoHistoryGetsTheBandValueUnchanged() {
        assertThat(EffortCalibration.factorOf(List.of(), MIN_SESSIONS, 0.5, 2.0))
                .isEqualTo(EffortCalibration.NEUTRAL);
    }

    @Test
    void aStudentWhoTakesLongerThanPlannedScalesUp() {
        List<StudySessionView> sessions = sessions(6, 50, 60);

        assertThat(EffortCalibration.factorOf(sessions, MIN_SESSIONS, 0.5, 2.0))
                .as("the band says fifty minutes and this student needs sixty; the plan should "
                        + "reserve sixty for them and not report that they are behind")
                .isEqualTo(1.2);
    }

    @Test
    void aStudentWhoIsFasterThanPlannedScalesDown() {
        assertThat(EffortCalibration.factorOf(sessions(6, 50, 40), MIN_SESSIONS, 0.5, 2.0))
                .isEqualTo(0.8);
    }

    @Test
    void tooLittleHistoryIsNoHistory() {
        List<StudySessionView> sessions = sessions(MIN_SESSIONS - 1, 50, 10);

        assertThat(EffortCalibration.factorOf(sessions, MIN_SESSIONS, 0.5, 2.0))
                .as("a ratio computed from four sessions is not a measurement, and this one "
                        + "would turn every topic in the plan into ten minutes")
                .isEqualTo(EffortCalibration.NEUTRAL);
    }

    @Test
    void theFactorIsClamped() {
        assertThat(EffortCalibration.factorOf(sessions(6, 50, 5), MIN_SESSIONS, 0.5, 2.0))
                .as("a tenth of the planned time is a student who logs a session and walks "
                        + "away, not a student who learns ten times faster")
                .isEqualTo(0.5);
        assertThat(EffortCalibration.factorOf(sessions(6, 10, 500), MIN_SESSIONS, 0.5, 2.0))
                .isEqualTo(2.0);
    }

    @Test
    void sessionsWithoutBothNumbersAreIgnored() {
        List<StudySessionView> sessions = new ArrayList<>(sessions(5, 50, 60));
        // Self-directed: no planned duration, so it says nothing about the estimate.
        sessions.add(session(null, 200));
        // Abandoned: no recorded duration, so it says nothing about how long the topic takes.
        sessions.add(session(50, null));

        assertThat(EffortCalibration.factorOf(sessions, MIN_SESSIONS, 0.5, 2.0))
                .as("counting either would let a session that measured nothing move the estimate")
                .isEqualTo(1.2);
    }

    @Test
    void aPlannedDurationOfZeroIsIgnored() {
        List<StudySessionView> sessions = new ArrayList<>(sessions(5, 50, 50));
        sessions.add(session(0, 30));

        assertThat(EffortCalibration.factorOf(sessions, MIN_SESSIONS, 0.5, 2.0))
                .isEqualTo(1.0);
    }

    private static List<StudySessionView> sessions(int count, Integer planned, Integer actual) {
        List<StudySessionView> sessions = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            sessions.add(session(planned, actual));
        }
        return sessions;
    }

    private static StudySessionView session(Integer planned, Integer actual) {
        return new StudySessionView(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                planned == null ? null : UUID.randomUUID(), SessionKind.STUDY,
                planned == null ? SessionSource.SELF_DIRECTED : SessionSource.FROM_PLAN,
                actual == null ? SessionStatus.ABANDONED : SessionStatus.COMPLETED,
                NOW, NOW.plusSeconds(3600), planned, actual,
                actual == null ? null : DurationSource.MEASURED, null);
    }
}
