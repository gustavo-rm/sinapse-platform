package br.com.sinapse.platform.identity.internal.service;

import br.com.sinapse.platform.identity.api.AccountAccessPolicy;
import br.com.sinapse.platform.identity.api.AccountStatus;
import br.com.sinapse.platform.identity.api.ConsentPurpose;
import br.com.sinapse.platform.identity.internal.persistence.AccountRepository;
import br.com.sinapse.platform.identity.internal.persistence.ConsentRecordRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The access gate of section 5.5.
 *
 * <p>Two conditions, in this order and nowhere else: the account is active, and the purpose
 * has a consent in force. The first is not redundant. Invariant 1 does say that an active
 * account has valid essential consent, but not the converse: a suspended account can still
 * hold a perfectly valid research consent, and processing its data would be exactly the
 * thing the suspension exists to stop.
 *
 * <p>Nothing here is cached. A revocation suspends the account in its own transaction, and
 * the whole point of ADR 0010 was that the next request has to see it.
 */
@Service
public class DefaultAccountAccessPolicy implements AccountAccessPolicy {

    private final AccountRepository accounts;
    private final ConsentRecordRepository consents;

    /**
     * @param accounts accounts
     * @param consents consent records
     */
    public DefaultAccountAccessPolicy(AccountRepository accounts, ConsentRecordRepository consents) {
        this.accounts = accounts;
        this.consents = consents;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canProcessLearningData(UUID accountId) {
        return allows(accountId, ConsentPurpose.LEARNING_DATA_PROCESSING);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canShareWithInstitution(UUID accountId) {
        return allows(accountId, ConsentPurpose.INSTITUTION_SHARING);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canUseForResearch(UUID accountId) {
        return allows(accountId, ConsentPurpose.ACADEMIC_RESEARCH);
    }

    private boolean allows(UUID accountId, ConsentPurpose purpose) {
        if (accountId == null) {
            return false;
        }
        boolean active = accounts.findById(accountId)
                .map(account -> account.status() == AccountStatus.ACTIVE)
                .orElse(false);
        return active && consents
                .findByAccountIdAndPurposeAndRevokedAtIsNull(accountId, purpose)
                .isPresent();
    }
}
