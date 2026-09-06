package br.com.sinapse.platform.identity.internal.service;

import br.com.sinapse.platform.identity.api.AccountStatus;
import br.com.sinapse.platform.identity.api.AccountLifecycle;
import br.com.sinapse.platform.identity.api.ConsentGrantedBy;
import br.com.sinapse.platform.identity.api.ConsentPurpose;
import br.com.sinapse.platform.identity.internal.config.IdentityProperties;
import br.com.sinapse.platform.identity.internal.domain.Account;
import br.com.sinapse.platform.identity.internal.domain.ConsentEvidence;
import br.com.sinapse.platform.identity.internal.domain.ConsentRecord;
import br.com.sinapse.platform.identity.internal.domain.TermsVersion;
import br.com.sinapse.platform.identity.internal.error.AccountTransitionException;
import br.com.sinapse.platform.identity.internal.error.ConsentAlreadyGrantedException;
import br.com.sinapse.platform.identity.internal.error.ConsentNotFoundException;
import br.com.sinapse.platform.identity.internal.error.EssentialConsentMissingException;
import br.com.sinapse.platform.identity.internal.error.GuardianRequiredException;
import br.com.sinapse.platform.identity.internal.persistence.AccountRepository;
import br.com.sinapse.platform.identity.internal.persistence.ConsentRecordRepository;
import br.com.sinapse.platform.shared.web.problem.ApiErrorType;
import br.com.sinapse.platform.shared.web.problem.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The single writer of consent records and of the status of an account.
 *
 * <p>Invariant 1 — an active account has a valid consent for every essential purpose, and
 * the grantor of each matches the holder's age when it was granted — crosses two
 * aggregates. In a monolith over one database that is held transactionally, by one service
 * that writes both, and section 5.3 of the architecture document says so explicitly. If
 * identity ever becomes a service of its own, the invariant becomes eventually consistent
 * and the model has to be revisited; that is recorded in ADR 0004, not worked around here.
 *
 * <p>Everything that could break the invariant therefore lives in this class:
 *
 * <ul>
 *   <li>granting, because the grantor is derived from the age and not supplied;</li>
 *   <li>revoking, because revoking an essential purpose has to suspend the account in the
 *       same transaction, or the gate would be open on data nobody consented to;</li>
 *   <li>activation, because it is the moment the invariant is asserted;</li>
 *   <li>reaffirmation, because it revokes and grants at once and the account must never be
 *       observable in between;</li>
 *   <li>suspension and anonymisation, because both revoke sessions in the same transaction
 *       (ADR 0010).</li>
 * </ul>
 *
 * <p>{@code ConsentServiceIsTheOnlyWriterTest} fails the build when another class writes a
 * consent record or moves an account between states.
 */
@Service
public class ConsentService implements AccountLifecycle {

    private final AccountRepository accounts;
    private final ConsentRecordRepository consents;
    private final TermsService terms;
    private final SessionService sessions;
    private final IdentityProperties properties;
    private final Clock clock;

    /**
     * @param accounts   accounts
     * @param consents   consent records
     * @param terms      published wordings
     * @param sessions   sessions, revoked together with a suspension
     * @param properties configured age threshold and grace period
     * @param clock      application clock
     */
    public ConsentService(AccountRepository accounts, ConsentRecordRepository consents, TermsService terms,
            SessionService sessions, IdentityProperties properties, Clock clock) {
        this.accounts = accounts;
        this.consents = consents;
        this.terms = terms;
        this.sessions = sessions;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Records an act of consent.
     *
     * <p>The grantor is not a parameter. It is derived from the holder's age at this
     * instant, read in the holder's own zone, which is what makes invariant 3 impossible to
     * get wrong at the call site: a caller cannot record a minor's consent as the minor's
     * own by passing the wrong enum, because there is no enum to pass.
     *
     * @param accountId      holder
     * @param purpose        purpose being consented to
     * @param termsVersionId wording the client displayed
     * @param evidence       what was observed about the act
     * @return the record
     * @throws ConsentAlreadyGrantedException if the purpose already has a valid consent
     * @throws GuardianRequiredException      if the holder is below the threshold and has no
     *                                        guardian
     */
    @Transactional
    public ConsentRecord grant(UUID accountId, ConsentPurpose purpose, UUID termsVersionId,
            ConsentEvidence evidence) {

        Account account = require(accountId);
        if (account.status() == AccountStatus.ANONYMIZED) {
            throw new AccountTransitionException();
        }
        consents.findByAccountIdAndPurposeAndRevokedAtIsNull(accountId, purpose)
                .ifPresent(existing -> {
                    throw new ConsentAlreadyGrantedException();
                });

        TermsVersion wording = terms.requireCurrent(purpose, termsVersionId);
        return consents.save(record(account, purpose, wording, clock.instant(), evidence));
    }

    /**
     * Withdraws a consent.
     *
     * <p>Nothing is deleted: the record keeps its grant and gains a revocation, which is
     * what invariant 4 and the database trigger behind it are for.
     *
     * <p>For an essential purpose the account is suspended and its sessions are revoked
     * here, in this transaction. Doing it afterwards, or in a listener, would leave a window
     * in which the account is still usable without the consent that makes it lawful — and
     * that window is exactly what ADR 0010 refused to accept when it rejected stateless
     * tokens.
     *
     * @param accountId holder
     * @param purpose   purpose to withdraw
     * @throws ConsentNotFoundException if there is no valid consent for that purpose
     */
    @Transactional
    public void revoke(UUID accountId, ConsentPurpose purpose) {
        Account account = require(accountId);
        ConsentRecord record = consents.findByAccountIdAndPurposeAndRevokedAtIsNull(accountId, purpose)
                .orElseThrow(ConsentNotFoundException::new);

        Instant now = clock.instant();
        record.revoke(now);

        if (purpose.isEssential()) {
            account.suspend(now);
            sessions.revokeAll(accountId);
        }
    }

    /**
     * Decides where an account goes once it has proved control of its e-mail address.
     *
     * <p>This is where invariant 1 is asserted. An adult with valid essential consent
     * becomes active immediately. A holder below the threshold goes to
     * {@code PENDING_GUARDIAN_CONSENT} unless a guardian has been verified — and in this
     * version none ever is, because the guardian verification flow belongs to the next one.
     *
     * @param accountId holder
     * @throws EssentialConsentMissingException if an essential purpose has no valid consent,
     *                                          or one whose grantor does not match the age at
     *                                          the time
     */
    @Transactional
    public void activateAfterEmailVerification(UUID accountId) {
        Account account = require(accountId);
        if (account.status() != AccountStatus.PENDING_VERIFICATION
                && account.status() != AccountStatus.PENDING_GUARDIAN_CONSENT) {
            throw new AccountTransitionException();
        }

        Instant now = clock.instant();
        assertEssentialConsentIsValid(account);

        if (requiresGuardian(account, now) && !account.hasVerifiedGuardian()) {
            account.awaitGuardianConsent();
            return;
        }
        account.activate(now);
    }

    /**
     * Replaces a guardian's consent with the holder's own, once the holder has reached the
     * threshold.
     *
     * <p>Section 5.6: the account is not blocked at the birthday. The consent a guardian
     * gave was validly obtained and does not become void at that instant; keeping it
     * indefinitely is what would be indefensible, because the grantor stopped being the
     * right person. So the holder is asked, and has a grace period to answer.
     *
     * <p>The previous record is preserved. It is revoked and a new one is written, in this
     * order and in one transaction — which is the only way to do it, because the partial
     * unique index allows one valid consent per purpose and because a revoked essential
     * consent would otherwise suspend the account for the instant in between.
     *
     * @param accountId holder
     * @param evidence  what was observed about the act
     * @throws GuardianRequiredException if the holder has not reached the threshold, in
     *                                   which case consent is still the guardian's to give
     */
    @Transactional
    public void reaffirmMajority(UUID accountId, ConsentEvidence evidence) {
        Account account = require(accountId);
        if (account.status() != AccountStatus.ACTIVE && account.status() != AccountStatus.SUSPENDED) {
            throw new AccountTransitionException();
        }

        Instant now = clock.instant();
        if (requiresGuardian(account, now)) {
            throw new GuardianRequiredException();
        }

        LocalDate majorityDate = account.majorityDate(properties.consentAgeThreshold());
        for (ConsentPurpose purpose : ConsentPurpose.essentialPurposes()) {
            ConsentRecord current = consents
                    .findByAccountIdAndPurposeAndRevokedAtIsNull(accountId, purpose)
                    .orElse(null);
            if (current != null && isOwnConsentGivenAfter(account, current, majorityDate)) {
                continue;
            }
            if (current != null) {
                current.revoke(now);
                // The withdrawal has to reach the database before the replacement does.
                // Hibernate orders inserts ahead of updates, so without this the new record is
                // inserted while the old one still looks valid, and ux_active_consent — which
                // is doing exactly its job — rejects it.
                consents.flush();
            }
            TermsVersion wording = terms.currentFor(purpose)
                    .orElseThrow(() -> new ApiException(ApiErrorType.CONFLICT));
            consents.save(record(account, purpose, wording, now, evidence));
        }

        assertEssentialConsentIsValid(account);
        account.activate(now);
    }

    /**
     * Suspends an account and ends its sessions.
     *
     * @param accountId holder
     */
    @Override
    @Transactional
    public void suspend(UUID accountId) {
        Account account = require(accountId);
        account.suspend(clock.instant());
        sessions.revokeAll(accountId);
    }

    /**
     * Restores a suspended account to active.
     *
     * <p>The one caller is the cancellation of an erasure request: the request suspended the
     * account, and withdrawing it has to put that back. It is here rather than there because
     * this service is the only thing in the platform that moves an account between states, and
     * a second place that could activate one would be a second place invariant 1 can break.
     *
     * <p><strong>It re-asserts that invariant rather than assuming it.</strong> An account can
     * be suspended for more than one reason, and cancelling an erasure says nothing about a
     * consent that was withdrawn in the meantime. If an essential purpose has no valid consent
     * the account stays suspended and the caller is told, which is the same answer activation
     * after e-mail verification gives.
     *
     * @param accountId holder
     * @throws EssentialConsentMissingException if an essential purpose has no valid consent
     */
    @Override
    @Transactional
    public void reactivate(UUID accountId) {
        Account account = require(accountId);
        assertEssentialConsentIsValid(account);
        account.activate(clock.instant());
    }

    /**
     * Moves an account to its terminal state and ends its sessions.
     *
     * <p>What is erased and what survives belongs to the data rights context. What belongs
     * here is the transition itself and the revocation that has to accompany it, so that
     * the erasure service cannot leave a usable session behind by forgetting to.
     *
     * @param accountId holder
     */
    @Transactional
    public void anonymize(UUID accountId) {
        Account account = require(accountId);
        account.anonymize(clock.instant());
        sessions.revokeAll(accountId);
    }

    /**
     * Whether the essential consents of an account are all valid and all granted in the
     * holder's own name on or after the day they reached the threshold.
     *
     * <p>This is the derivable condition of section 5.6, expressed once and read both by the
     * daily sweep and by the reaffirmation itself. It needs no column: an account created in
     * adulthood satisfies it the moment it consents.
     *
     * @param account holder
     * @param history every consent record of that holder, valid or not
     * @return whether nothing further is owed
     */
    public boolean hasReaffirmedForMajority(Account account, List<ConsentRecord> history) {
        LocalDate majorityDate = account.majorityDate(properties.consentAgeThreshold());
        return history.stream()
                .filter(record -> record.purpose().isEssential())
                .anyMatch(record -> isOwnConsentGivenAfter(account, record, majorityDate));
    }

    /** The configured age from which consent is the holder's own to give. */
    public int consentAgeThreshold() {
        return properties.consentAgeThreshold();
    }

    /** The configured number of days a holder has to reaffirm after reaching the threshold. */
    public int majorityGracePeriodDays() {
        return properties.majorityGracePeriodDays();
    }

    private ConsentRecord record(Account account, ConsentPurpose purpose, TermsVersion wording,
            Instant grantedAt, ConsentEvidence evidence) {

        if (!requiresGuardian(account, grantedAt)) {
            // Invariant 3 is about the record, not about the account. A holder who has since
            // reached the threshold still has the guardian who answered for them as a minor,
            // and that row stays: it is the record of an act that happened. What may not
            // happen is a new consent carrying that guardian's name, and it cannot, because
            // this branch never puts one there.
            return ConsentRecord.grantedBySelf(UUID.randomUUID(), account.id(), purpose,
                    wording.id(), grantedAt, evidence);
        }
        if (account.guardian() == null) {
            throw new GuardianRequiredException();
        }
        return ConsentRecord.grantedByGuardian(UUID.randomUUID(), account.id(), purpose,
                wording.id(), account.guardian().id(), grantedAt, evidence);
    }

    /**
     * Invariant 1, in full: every essential purpose has a valid consent, and each of those
     * was granted by whoever the holder's age <em>at that moment</em> made appropriate.
     *
     * <p>The second half is the part that is easy to lose. A record written by an older
     * version of this code, by a repair script, or by a threshold that has since changed can
     * be perfectly valid and still have the wrong grantor, and an account activated on top
     * of it would carry consent nobody was entitled to give.
     */
    private void assertEssentialConsentIsValid(Account account) {
        for (ConsentPurpose purpose : ConsentPurpose.essentialPurposes()) {
            ConsentRecord record = consents
                    .findByAccountIdAndPurposeAndRevokedAtIsNull(account.id(), purpose)
                    .orElseThrow(EssentialConsentMissingException::new);

            ConsentGrantedBy expected = requiresGuardian(account, record.grantedAt())
                    ? ConsentGrantedBy.GUARDIAN
                    : ConsentGrantedBy.SELF;
            if (record.grantedBy() != expected) {
                throw new EssentialConsentMissingException();
            }
        }
    }

    private boolean requiresGuardian(Account account, Instant at) {
        return account.ageAt(at) < properties.consentAgeThreshold();
    }

    private boolean isOwnConsentGivenAfter(Account account, ConsentRecord record, LocalDate majorityDate) {
        return record.grantedBy() == ConsentGrantedBy.SELF
                && !LocalDate.ofInstant(record.grantedAt(), account.timeZone()).isBefore(majorityDate);
    }

    private Account require(UUID accountId) {
        return accounts.findById(accountId)
                .orElseThrow(() -> new ApiException(ApiErrorType.RESOURCE_NOT_FOUND));
    }
}
