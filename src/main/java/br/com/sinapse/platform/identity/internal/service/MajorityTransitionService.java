package br.com.sinapse.platform.identity.internal.service;

import br.com.sinapse.platform.identity.api.AccountStatus;
import br.com.sinapse.platform.identity.internal.domain.Account;
import br.com.sinapse.platform.identity.internal.domain.AccountTokenPurpose;
import br.com.sinapse.platform.identity.internal.domain.ConsentRecord;
import br.com.sinapse.platform.identity.internal.persistence.AccountRepository;
import br.com.sinapse.platform.identity.internal.persistence.ConsentRecordRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The daily pass over accounts whose holder has reached the consent age threshold.
 *
 * <p>Section 5.6 decided not to block anyone on their birthday. The consent a guardian gave
 * was validly obtained and does not become void at that instant; what would be indefensible
 * is keeping it forever, because the grantor stopped being the right person. So the holder
 * is asked, keeps the account throughout a grace period, and is suspended only when the
 * period runs out unanswered.
 *
 * <p>The condition needs no column. An account owes a reaffirmation when its holder has
 * reached the threshold and no consent for an essential purpose was granted in their own
 * name on or after that day. Accounts opened in adulthood satisfy it the moment they
 * consent, which is why this sweep does nothing at all in a population of adults — and why
 * it still has to exist, because the population will not stay that way.
 *
 * <p>Dates are read in each holder's own zone. A sweep that used one zone for everyone
 * would suspend somebody a day early somewhere on Earth.
 */
@Service
public class MajorityTransitionService {

    private static final Logger LOG = LoggerFactory.getLogger(MajorityTransitionService.class);

    private final AccountRepository accounts;
    private final ConsentRecordRepository consents;
    private final ConsentService consentService;
    private final AccountTokenService tokens;
    private final Clock clock;

    /**
     * @param accounts       accounts
     * @param consents       consent records, read in one batch for all candidates
     * @param consentService the single writer of account status
     * @param tokens         issuer of the reaffirmation token
     * @param clock          application clock, read here only for its zone, which is the one
     *                       the candidate filter is expressed in
     */
    public MajorityTransitionService(AccountRepository accounts, ConsentRecordRepository consents,
            ConsentService consentService, AccountTokenService tokens, Clock clock) {
        this.accounts = accounts;
        this.consents = consents;
        this.consentService = consentService;
        this.tokens = tokens;
        this.clock = clock;
    }

    /**
     * Runs one pass.
     *
     * <p>The instant is a parameter rather than a reading of the clock so that the rule can
     * be tested at a chosen date instead of at whatever date the suite happens to run on.
     * The scheduled job supplies the clock's instant.
     *
     * @param now instant to evaluate the population at
     * @return how many accounts were notified and how many were suspended
     */
    @Transactional
    public SweepResult sweep(Instant now) {
        List<Account> candidates = candidatesAt(now);
        if (candidates.isEmpty()) {
            return new SweepResult(0, 0);
        }

        Map<UUID, List<ConsentRecord>> history = historyOf(candidates);
        int notified = 0;
        int suspended = 0;

        for (Account account : candidates) {
            LocalDate today = LocalDate.ofInstant(now, account.timeZone());
            LocalDate majorityDate = account.majorityDate(consentService.consentAgeThreshold());

            if (today.isBefore(majorityDate)) {
                continue;
            }
            if (consentService.hasReaffirmedForMajority(account, history.getOrDefault(account.id(), List.of()))) {
                continue;
            }

            LocalDate deadline = majorityDate.plusDays(consentService.majorityGracePeriodDays());
            if (today.isAfter(deadline)) {
                consentService.suspend(account.id());
                suspended++;
            } else if (notifyOnce(account)) {
                notified++;
            }
        }

        if (notified > 0 || suspended > 0) {
            LOG.info("Majority sweep: {} account(s) notified within the grace period, {} suspended",
                    notified, suspended);
        }
        return new SweepResult(notified, suspended);
    }

    /**
     * Accounts that could already have reached the threshold, in any zone.
     *
     * <p>Deliberately one day wider than the application zone would suggest: whether the
     * holder has actually reached the threshold depends on their own zone, and the widest
     * offsets in use are just over half a day apart. Narrowing precisely would mean
     * converting an instant per row against a zone stored in that row; the sweep narrows
     * roughly here and decides exactly below.
     */
    private List<Account> candidatesAt(Instant now) {
        LocalDate bornOnOrBefore = LocalDate.ofInstant(now, clock.getZone())
                .minusYears(consentService.consentAgeThreshold())
                .plusDays(1);
        return accounts.findByStatusAndDateOfBirthLessThanEqual(AccountStatus.ACTIVE, bornOnOrBefore);
    }

    /**
     * The consent history of every candidate, in one query.
     *
     * <p>Architecture rule R7 applied to a job rather than to a screen: reading the history
     * per account would issue one query per candidate, and the sweep runs over the whole
     * population every day.
     */
    private Map<UUID, List<ConsentRecord>> historyOf(List<Account> candidates) {
        List<UUID> ids = candidates.stream().map(Account::id).toList();
        return consents.findByAccountIdIn(ids).stream()
                .collect(Collectors.groupingBy(ConsentRecord::accountId, HashMap::new, Collectors.toList()));
    }

    /**
     * Issues a reaffirmation token, unless one is already outstanding.
     *
     * <p>The sweep sees the same account every day of the grace period. Without this check
     * it would turn one request into thirty messages and thirty usable tokens.
     */
    private boolean notifyOnce(Account account) {
        if (tokens.hasOutstanding(account.id(), AccountTokenPurpose.MAJORITY_REAFFIRMATION)) {
            return false;
        }
        tokens.issueAndDeliver(account, AccountTokenPurpose.MAJORITY_REAFFIRMATION);
        return true;
    }

    /**
     * What one pass did.
     *
     * @param notified  accounts inside the grace period that were asked to reaffirm
     * @param suspended accounts whose grace period ran out unanswered
     */
    public record SweepResult(int notified, int suspended) {
    }
}
