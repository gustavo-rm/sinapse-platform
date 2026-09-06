package br.com.sinapse.platform.readmodel.internal.service;

import br.com.sinapse.platform.identity.api.AccountDirectory;
import br.com.sinapse.platform.identity.api.AccountStateView;
import br.com.sinapse.platform.learningrecord.api.StudyHistory;
import br.com.sinapse.platform.learningrecord.api.StudySessionView;
import br.com.sinapse.platform.planning.api.PlanningDirectory;
import br.com.sinapse.platform.planning.api.StudyPlanView;
import br.com.sinapse.platform.readmodel.api.StudentStateView;
import br.com.sinapse.platform.readmodel.internal.error.NotReadableException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code EstadoDoAluno}: what the initial screen needs, from three modules, in one call.
 *
 * <p>Section 3.1 of the API contract. The caller reads themselves and nobody else — the route
 * takes no account identifier, which is what makes it impossible to write one that reads
 * somebody else's state.
 *
 * <p><strong>The access gate is applied to part of the answer, not to the call.</strong> A
 * holder whose learning data may not be processed — suspended, or having withdrawn the
 * essential consent — still gets a reply, because this screen is how they find out that is the
 * case and what to do about it. What they do not get is a plan identifier, a job identifier, an
 * open session or a readiness flag: the platform is not entitled to act on that data, and
 * reporting it would be acting on it. Refusing the whole call instead would leave the holder
 * with a 403 and no explanation, which is the one outcome that helps nobody.
 */
@Service
public class StudentStateReadModel {

    private final AccountDirectory accounts;
    private final PlanningDirectory planning;
    private final StudyHistory history;
    private final ReadModelAccess access;

    /**
     * @param accounts the account's own state
     * @param planning plan, generation job and readiness
     * @param history  the session left running, if any
     * @param access   the single gate these reads consult
     */
    public StudentStateReadModel(AccountDirectory accounts, PlanningDirectory planning,
            StudyHistory history, ReadModelAccess access) {
        this.accounts = accounts;
        this.planning = planning;
        this.history = history;
        this.access = access;
    }

    /**
     * The state of the caller's account.
     *
     * @param accountId the caller
     * @return their state
     * @throws NotReadableException if there is no such account
     */
    @Transactional(readOnly = true)
    public StudentStateView of(UUID accountId) {
        AccountStateView state = accounts.stateOf(accountId).orElseThrow(NotReadableException::new);

        if (!access.mayReadOwnLearningData(accountId)) {
            return new StudentStateView(state.status(), state.timeZone(), state.pendingConsents(),
                    state.requiresMajorityReaffirmation(), false, null, null, null);
        }

        return new StudentStateView(
                state.status(),
                state.timeZone(),
                state.pendingConsents(),
                state.requiresMajorityReaffirmation(),
                planning.isReadyToPlan(accountId),
                planning.activePlanOf(accountId).map(StudyPlanView::id).orElse(null),
                planning.unfinishedGenerationRequestIdOf(accountId).orElse(null),
                history.openSessionOf(accountId).map(StudySessionView::id).orElse(null));
    }
}
