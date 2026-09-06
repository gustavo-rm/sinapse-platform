package br.com.sinapse.platform.datarights.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.sinapse.platform.datarights.api.ErasureRequestStatus;
import br.com.sinapse.platform.datarights.api.ErasureRequestView;
import br.com.sinapse.platform.datarights.internal.error.ErasureAlreadyRequestedException;
import br.com.sinapse.platform.datarights.internal.error.ErasureRequestNotOpenException;
import br.com.sinapse.platform.datarights.internal.error.UnknownErasureRequestException;
import br.com.sinapse.platform.datarights.support.DataRightsIntegrationTest;
import br.com.sinapse.platform.identity.api.AccountStatus;
import br.com.sinapse.platform.identity.internal.domain.Account;
import br.com.sinapse.platform.identity.internal.service.AuthenticationService;
import br.com.sinapse.platform.identity.support.IdentityFixtures;
import br.com.sinapse.platform.learningrecord.api.SessionKind;
import br.com.sinapse.platform.planning.internal.error.PlanningDataNotProcessableException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The seven days between asking to be erased and being erased.
 *
 * <p>The window is the reversibility ADR 0011 asks for, and it only exists if the holder can
 * reach it. Suspending the account and revoking its sessions the moment the request is made,
 * which the ADR also asks for, would otherwise lock them out of the very thing they are being
 * given time to reconsider.
 */
class ErasureLifecycleIntegrationTest extends DataRightsIntegrationTest {

    @Autowired
    private AuthenticationService authentication;

    @Test
    void askingSuspendsTheAccountAndRevokesItsSessionsAtOnce() {
        Account student = fullyPopulatedStudent();
        tokenFor(student);
        assertThat(openSessionCount(student.id())).isPositive();

        ErasureRequestView request = erasureRequests.request(student.id());

        assertThat(request.status()).isEqualTo(ErasureRequestStatus.REQUESTED);
        assertThat(Duration.between(request.requestedAt(), request.effectiveAt()))
                .as("seven days, and configurable, because whether that is adequate is one of "
                        + "the three points ADR 0011 leaves for legal confirmation")
                .isEqualTo(Duration.ofDays(7));
        assertThat(accountColumn(student.id(), "status", String.class))
                .as("processing stops when the holder says so, not a week later")
                .isEqualTo(AccountStatus.SUSPENDED.name());
        assertThat(openSessionCount(student.id())).isZero();
        assertThat(countFor("study_session", student.id()))
                .as("nothing is erased yet: that is what the window is for")
                .isPositive();
    }

    /**
     * The whole point of the window, and the thing that makes it real.
     */
    @Test
    void theHolderCanStillSignInDuringTheWindowAndDoNothingElse() {
        Account student = fullyPopulatedStudent();
        erasureRequests.request(student.id());

        assertThatCode(() -> authentication.authenticate(student.email(),
                IdentityFixtures.DEFAULT_PASSWORD, "203.0.113.7", "integration-test"))
                .as("a suspension that locked the holder out would make the seven days a "
                        + "countdown they could only watch")
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> goals.set(student.id(), subject().id(), null, 3))
                .as("and nothing else: every other use case asks AccountAccessPolicy at its "
                        + "entry, and it answers false for a suspended account")
                .isInstanceOf(PlanningDataNotProcessableException.class);
        assertThatThrownBy(() -> studySessions.start(student.id(), topic().id(), null,
                SessionKind.STUDY, 50))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void withdrawingTheRequestRestoresTheAccountWithItsDataIntact() {
        Account student = fullyPopulatedStudent();
        Map<String, Integer> before = erasableCountsOf(student.id());
        ErasureRequestView request = erasureRequests.request(student.id());

        ErasureRequestView cancelled = erasureRequests.cancel(student.id(), request.id());

        assertThat(cancelled.status()).isEqualTo(ErasureRequestStatus.CANCELLED);
        assertThat(cancelled.cancelledAt()).isNotNull();
        assertThat(accountColumn(student.id(), "status", String.class))
                .isEqualTo(AccountStatus.ACTIVE.name());
        assertThat(erasableCountsOf(student.id()))
                .as("nothing was touched, so there is nothing to restore")
                .isEqualTo(before);

        assertThatCode(() -> goals.set(student.id(), subject().id(), null, 3))
                .as("and the account works again")
                .doesNotThrowAnyException();
    }

    @Test
    void aWithdrawnRequestDoesNotStopTheSweep() {
        Account student = fullyPopulatedStudent();
        ErasureRequestView request = erasureRequests.request(student.id());
        erasureRequests.cancel(student.id(), request.id());
        jdbc.update("update erasure_request set requested_at = now() - interval '2 seconds', "
                + "effective_at = now() - interval '1 second' where id = ?", request.id());

        assertThat(erasureJob.runOnce())
                .as("the sweep only takes requests that are still open")
                .isZero();
        assertThat(countFor("study_session", student.id())).isPositive();
    }

    @Test
    void aSecondOpenRequestIsRefused() {
        Account student = fullyPopulatedStudent();
        erasureRequests.request(student.id());

        assertThatThrownBy(() -> erasureRequests.request(student.id()))
                .as("a second request would do nothing the first is not already doing, and it "
                        + "would move the window, which is the protection the delay exists for")
                .isInstanceOf(ErasureAlreadyRequestedException.class);
    }

    @Test
    void aRequestThatHasBeenCarriedOutCannotBeWithdrawn() {
        Account student = fullyPopulatedStudent();
        UUID requestId = eraseNow(student);

        assertThatThrownBy(() -> erasureRequests.cancel(student.id(), requestId))
                .as("by then there is nothing to withdraw: the data is gone")
                .isInstanceOf(ErasureRequestNotOpenException.class);
    }

    @Test
    void aRequestOfAnotherAccountIsNotReachable() {
        Account owner = fullyPopulatedStudent();
        Account other = student();
        ErasureRequestView request = erasureRequests.request(owner.id());

        assertThatThrownBy(() -> erasureRequests.cancel(other.id(), request.id()))
                .as("a request that exists and one belonging to somebody else answer the same "
                        + "way; separating them would disclose that an account is being erased")
                .isInstanceOf(UnknownErasureRequestException.class);
        assertThatThrownBy(() -> erasureRequests.cancel(owner.id(), UUID.randomUUID()))
                .isInstanceOf(UnknownErasureRequestException.class);
    }

    @Test
    void theRequestKeepsNoCopyOfWhatItRemoves() {
        Account student = fullyPopulatedStudent();
        String email = accountColumn(student.id(), "email", String.class);
        UUID requestId = eraseNow(student);

        Map<String, Object> row = jdbc.queryForMap("select * from erasure_request where id = ?",
                requestId);

        assertThat(row.keySet())
                .as("storing an e-mail or a copy of the data would reintroduce exactly what the "
                        + "request removes, in the one row built to outlive it")
                .containsExactlyInAnyOrder("id", "account_id", "status", "requested_at",
                        "effective_at", "completed_at", "cancelled_at", "failure_reason");
        assertThat(row.values().stream().map(String::valueOf))
                .noneSatisfy(value -> assertThat(value).contains(email));
    }

    private int openSessionCount(UUID accountId) {
        return jdbc.queryForObject(
                "select count(*) from user_session where account_id = ? and revoked_at is null",
                Integer.class, accountId);
    }

    private Map<String, Integer> erasableCountsOf(UUID accountId) {
        Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        erasedTables().forEach(table -> counts.put(table, countFor(table, accountId)));
        counts.put("planned_session", plannedSessionCountFor(accountId));
        return counts;
    }
}
