package br.com.sinapse.platform.educational.internal.web;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.sinapse.platform.educational.api.VisibilityScope;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The reversal point of decision P1.
 *
 * <p>Nothing produces a limited scope today, and the API always answers {@code ALL}. The
 * branch exists so that reverting to a per-subject scope is a change to one expression rather
 * than a sweep across every caller — and a reversal point that has never been run is a claim
 * rather than a mechanism, which is why this test exercises the branch nothing reaches.
 *
 * <p>It lives in the package it tests, because the renderer is deliberately package-private:
 * publishing it would invite a second caller, and the point is that there is one.
 */
class VisibilityScopeRenderingTest {

    @Test
    void theIntegralScopeSaysThatOtherSubjectsAreIncluded() {
        InvitePreviewResponse.VisibilityDescription described =
                InviteController.describe(VisibilityScope.ALL);

        assertThat(described.scope()).isEqualTo("ALL");
        assertThat(described.subjectIds()).isEmpty();
        assertThat(described.description())
                .as("the whole reason the preview exists: the student has to be told that the "
                        + "teacher will see subjects that have nothing to do with this classroom")
                .contains("not part of this classroom");
    }

    @Test
    void aLimitedScopeAlreadyRendersCorrectlyIfItIsEverProduced() {
        UUID subject = UUID.randomUUID();

        InvitePreviewResponse.VisibilityDescription described =
                InviteController.describe(new VisibilityScope.Subjects(Set.of(subject)));

        assertThat(described.scope()).isEqualTo("SUBJECTS");
        assertThat(described.subjectIds()).containsExactly(subject);
        assertThat(described.description()).contains("subjects of this classroom only");
    }
}
