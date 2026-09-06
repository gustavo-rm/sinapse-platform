package br.com.sinapse.platform.learningrecord.internal.service;

import br.com.sinapse.platform.identity.api.AccountAccessPolicy;
import br.com.sinapse.platform.learningrecord.internal.error.LearningDataNotProcessableException;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The one place this module asks whether it may touch a student's learning data.
 *
 * <p>Section 5.5 of the architecture document and section 10 of {@code CLAUDE.md}: the gate
 * is consulted at the entry of the use cases and nowhere else, and no service here keeps an
 * opinion of its own about account status or consent. A suspended account and a withdrawn
 * essential consent both stop study being recorded, and neither fact is read here — the
 * answer comes from {@code identity} and this class only turns a {@code false} into a
 * response the caller can read.
 *
 * <p>It exists as a component rather than as a line repeated in four services because the
 * rule will move: the legal question behind it is open, and an edit that has thirty targets
 * is an edit that will miss one.
 */
@Component
public class LearningRecordAccess {

    private final AccountAccessPolicy accounts;

    /**
     * @param accounts the access gate of the identity module
     */
    public LearningRecordAccess(AccountAccessPolicy accounts) {
        this.accounts = accounts;
    }

    /**
     * Refuses unless the account's learning data may be processed.
     *
     * @param accountId student being acted upon
     * @throws LearningDataNotProcessableException if it may not
     */
    public void requireProcessable(UUID accountId) {
        if (!accounts.canProcessLearningData(accountId)) {
            throw new LearningDataNotProcessableException();
        }
    }
}
