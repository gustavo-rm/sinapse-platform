package br.com.sinapse.platform.planning.internal.service;

import br.com.sinapse.platform.identity.api.AccountAccessPolicy;
import br.com.sinapse.platform.planning.internal.error.PlanningDataNotProcessableException;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The one place this module asks whether it may touch a student's learning data.
 *
 * <p>Section 5.5 of the architecture document and section 10 of {@code CLAUDE.md}: the gate is
 * consulted at the entry of the use cases and nowhere else, and no service here keeps an
 * opinion of its own about account status or consent. It exists as a component rather than as
 * a line repeated in four services because the rule will move — the legal question behind it
 * is open — and an edit with thirty targets is an edit that will miss one.
 */
@Component
public class PlanningAccess {

    private final AccountAccessPolicy accounts;

    /**
     * @param accounts the access gate of the identity module
     */
    public PlanningAccess(AccountAccessPolicy accounts) {
        this.accounts = accounts;
    }

    /**
     * Refuses unless the account's learning data may be processed.
     *
     * @param accountId student being acted upon
     * @throws PlanningDataNotProcessableException if it may not
     */
    public void requireProcessable(UUID accountId) {
        if (!accounts.canProcessLearningData(accountId)) {
            throw new PlanningDataNotProcessableException();
        }
    }
}
