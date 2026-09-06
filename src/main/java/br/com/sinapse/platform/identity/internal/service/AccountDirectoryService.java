package br.com.sinapse.platform.identity.internal.service;

import br.com.sinapse.platform.identity.api.AccountDirectory;
import br.com.sinapse.platform.identity.api.AccountStateView;
import br.com.sinapse.platform.identity.api.ConsentPurpose;
import br.com.sinapse.platform.identity.internal.domain.Account;
import br.com.sinapse.platform.identity.internal.domain.ConsentRecord;
import br.com.sinapse.platform.identity.internal.persistence.AccountRepository;
import br.com.sinapse.platform.identity.internal.persistence.ConsentRecordRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Answers the questions another context asks about an account.
 *
 * <p>Read-only, and narrow on purpose: see {@link AccountDirectory}. An unknown account answers
 * empty rather than raising, because the caller is a composition layer that has already been
 * told by {@code AccountAccessPolicy} whether it may proceed.
 *
 * <p>Nothing here decides anything. {@link #stateOf} reports two derived conditions — which
 * purposes are undecided and whether a reaffirmation is owed — and both are computed from the
 * definitions that already exist, {@link ConsentPurpose#isRequestedOnItsOwn()} and
 * {@link ConsentService#hasReaffirmedForMajority}. A second definition written here would be a
 * screen that disagrees with the daily sweep, which is exactly the failure the holder would
 * experience as an account suspended without warning.
 */
@Service
@Transactional(readOnly = true)
public class AccountDirectoryService implements AccountDirectory {

    private final AccountRepository accounts;
    private final ConsentRecordRepository consents;
    private final ConsentService consentService;
    private final Clock clock;

    /**
     * @param accounts       accounts
     * @param consents       consent records, read whole because the reaffirmation condition is
     *                       about acts that happened and not about what is in force now
     * @param consentService the definitions of the consent rules, read and never re-stated
     * @param clock          application clock, read for the instant the state is evaluated at
     */
    public AccountDirectoryService(AccountRepository accounts, ConsentRecordRepository consents,
            ConsentService consentService, Clock clock) {
        this.accounts = accounts;
        this.consents = consents;
        this.consentService = consentService;
        this.clock = clock;
    }

    @Override
    public Optional<ZoneId> timeZoneOf(UUID accountId) {
        return accounts.findById(accountId).map(Account::timeZone);
    }

    @Override
    public Optional<AccountStateView> stateOf(UUID accountId) {
        return accounts.findById(accountId).map(account -> {
            List<ConsentRecord> history = consents.findByAccountIdOrderByGrantedAtDesc(accountId);
            return new AccountStateView(
                    account.id(),
                    account.status(),
                    account.timeZone(),
                    pendingConsents(history),
                    requiresMajorityReaffirmation(account, history));
        });
    }

    /**
     * Purposes the holder has never acted on, and which this platform raises unprompted.
     *
     * <p>Never acted on rather than not in force: a holder who withdrew a consent has decided,
     * and listing it as pending would turn the screen into a request to reconsider. A consent
     * that has to be asked for repeatedly is not freely given, which is the condition ADR 0004
     * exists to protect.
     */
    private static Set<ConsentPurpose> pendingConsents(List<ConsentRecord> history) {
        Set<ConsentPurpose> decided = history.stream()
                .map(ConsentRecord::purpose)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(ConsentPurpose.class)));

        return EnumSet.allOf(ConsentPurpose.class).stream()
                .filter(ConsentPurpose::isRequestedOnItsOwn)
                .filter(purpose -> !decided.contains(purpose))
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(ConsentPurpose.class)));
    }

    /**
     * Whether the holder has reached the consent age without reaffirming in their own name.
     *
     * <p>Read in the holder's own zone, like the sweep: evaluating it in the server's would
     * announce the obligation a day early or a day late somewhere on Earth.
     */
    private boolean requiresMajorityReaffirmation(Account account, List<ConsentRecord> history) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), account.timeZone());
        LocalDate majorityDate = account.majorityDate(consentService.consentAgeThreshold());
        return !today.isBefore(majorityDate)
                && !consentService.hasReaffirmedForMajority(account, history);
    }
}
