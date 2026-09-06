package br.com.sinapse.platform.identity.internal.service;

import br.com.sinapse.platform.identity.api.AccountDirectory;
import br.com.sinapse.platform.identity.internal.domain.Account;
import br.com.sinapse.platform.identity.internal.persistence.AccountRepository;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Answers the one question another context asks about an account.
 *
 * <p>Read-only, and narrow on purpose: see {@link AccountDirectory}. An unknown account answers
 * empty rather than raising, because the caller is a composition layer that has already been
 * told by {@code AccountAccessPolicy} whether it may proceed.
 */
@Service
@Transactional(readOnly = true)
public class AccountDirectoryService implements AccountDirectory {

    private final AccountRepository accounts;

    /**
     * @param accounts accounts
     */
    public AccountDirectoryService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    public Optional<ZoneId> timeZoneOf(UUID accountId) {
        return accounts.findById(accountId).map(Account::timeZone);
    }
}
