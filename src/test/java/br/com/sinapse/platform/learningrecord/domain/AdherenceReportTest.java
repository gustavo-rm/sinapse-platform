package br.com.sinapse.platform.learningrecord.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.sinapse.platform.learningrecord.api.AdherenceReport;
import br.com.sinapse.platform.learningrecord.api.RecallRating;
import br.com.sinapse.platform.learningrecord.api.TopicRecallTrajectory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The arithmetic and the defensive copies of the published read types. */
class AdherenceReportTest {

    @Test
    void theRatioIsExecutedOverPlanned() {
        AdherenceReport report = new AdherenceReport(8, 6);

        assertThat(report.notExecuted()).isEqualTo(2);
        assertThat(report.ratio()).hasValue(0.75);
    }

    @Test
    void nothingPlannedHasNoRatio() {
        AdherenceReport report = new AdherenceReport(0, 0);

        assertThat(report.ratio())
                .as("zero would read as a student who followed nothing and one as a perfect "
                        + "record; a panel showing either would assert what the data does not")
                .isEmpty();
        assertThat(report.notExecuted()).isZero();
    }

    @Test
    void countsThatCouldNotHaveComeFromCountingAreRefused() {
        assertThatThrownBy(() -> new AdherenceReport(3, 4))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AdherenceReport(-1, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aTrajectoryDoesNotChangeUnderItsCaller() {
        List<TopicRecallTrajectory.Point> mutable = new ArrayList<>();
        mutable.add(new TopicRecallTrajectory.Point(Instant.parse("2026-09-01T10:00:00Z"),
                RecallRating.HARD));
        TopicRecallTrajectory trajectory = new TopicRecallTrajectory(UUID.randomUUID(), mutable);

        mutable.add(new TopicRecallTrajectory.Point(Instant.parse("2026-09-02T10:00:00Z"),
                RecallRating.GOOD));

        assertThat(trajectory.points()).hasSize(1);
    }
}
