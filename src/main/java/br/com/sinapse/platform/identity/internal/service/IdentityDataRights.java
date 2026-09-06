package br.com.sinapse.platform.identity.internal.service;

import br.com.sinapse.platform.identity.internal.domain.Account;
import br.com.sinapse.platform.identity.internal.domain.ConsentRecord;
import br.com.sinapse.platform.identity.internal.persistence.AccountRepository;
import br.com.sinapse.platform.identity.internal.persistence.AccountTokenRepository;
import br.com.sinapse.platform.identity.internal.persistence.ConsentRecordRepository;
import br.com.sinapse.platform.identity.internal.persistence.UserSessionRepository;
import br.com.sinapse.platform.shared.datarights.ModuleDataRights;
import br.com.sinapse.platform.shared.datarights.ModuleExport;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * What this module owes the holder of an account.
 *
 * <p>The only module where something survives, and the reasons differ per row. Sessions,
 * tokens and the guardian are removed: none of them proves anything and the guardian is a
 * third party with no basis for retention once the holder's data is gone. The consent records
 * survive with their personal data cleared, because they are the proof of the legal basis for
 * treatment that already happened and the burden of that proof is the controller's. The account
 * itself survives as an emptied shell, because the consent records and the ended enrollments
 * both point at it and neither can point at nothing.
 *
 * <p>It runs last. Nothing here is referenced by another module's rows, so the order is not a
 * constraint — but emptying the account is the step that makes the erasure visible, and having
 * it happen after everything else means a rollback never leaves a shell with data hanging off
 * it.
 */
@Component
@Order(IdentityDataRights.ORDER)
public class IdentityDataRights implements ModuleDataRights {

    /** Last. The account shell is what the surviving records hang from. */
    public static final int ORDER = 100;

    private static final Logger LOG = LoggerFactory.getLogger(IdentityDataRights.class);

    private final AccountRepository accounts;
    private final ConsentRecordRepository consents;
    private final UserSessionRepository sessions;
    private final AccountTokenRepository tokens;
    private final ConsentService consentService;

    /**
     * @param accounts       accounts
     * @param consents       consent records
     * @param sessions       server-side sessions
     * @param tokens         verification and reset tokens
     * @param consentService the one thing that moves an account between states
     */
    public IdentityDataRights(AccountRepository accounts, ConsentRecordRepository consents,
            UserSessionRepository sessions, AccountTokenRepository tokens,
            ConsentService consentService) {
        this.accounts = accounts;
        this.consents = consents;
        this.sessions = sessions;
        this.tokens = tokens;
        this.consentService = consentService;
    }

    @Override
    public String moduleName() {
        return "identity";
    }

    @Override
    @Transactional
    public void eraseFor(UUID accountId) {
        int erasedSessions = sessions.eraseFor(accountId);
        int erasedTokens = tokens.eraseFor(accountId);

        List<ConsentRecord> records = consents.findByAccountIdOrderByGrantedAtDesc(accountId);
        records.forEach(ConsentRecord::clearPersonalData);
        // The clearing has to reach the database before the guardian row does, because the
        // foreign key from the consent record is what would otherwise hold that row in place.
        consents.flush();

        // Empties the shell and removes the guardian with it. It goes through the service that
        // owns account transitions, so that this is not a second place an account can change
        // state.
        consentService.anonymize(accountId);

        LOG.info("Erasure removed {} sessions and {} tokens, and cleared the personal data of "
                + "{} consent records", erasedSessions, erasedTokens, records.size());
    }

    @Override
    @Transactional(readOnly = true)
    public ModuleExport exportFor(UUID accountId) {
        Map<String, List<Object>> collections = new LinkedHashMap<>();
        accounts.findById(accountId)
                .ifPresent(account -> collections.put("account", List.of(holderOf(account))));
        collections.put("consents", consents.findByAccountIdOrderByGrantedAtDesc(accountId).stream()
                .map(IdentityDataRights::consentOf)
                .toList());
        collections.put("sessions", sessions
                .findByAccountIdAndRevokedAtIsNullOrderByCreatedAtDesc(accountId).stream()
                .map(session -> (Object) new SessionEntry(session.createdAt(), session.lastSeenAt(),
                        session.absoluteExpiresAt(), session.userAgent()))
                .toList());
        return new ModuleExport(moduleName(), collections);
    }

    private static Object holderOf(Account account) {
        return new HolderEntry(account.email(), account.dateOfBirth(), account.timeZone(),
                account.status().name(), account.createdAt(), account.activatedAt(),
                account.roles().stream().map(Enum::name).collect(
                        java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new)));
    }

    private static Object consentOf(ConsentRecord record) {
        return new ConsentEntry(record.purpose().name(), record.grantedBy().name(),
                record.grantedAt(), record.revokedAt(), record.evidence() == null);
    }

    /**
     * The holder, as they see themselves.
     *
     * <p>The password hash is not here and must never be. It is not the holder's data in any
     * useful sense, and an export that carried it would put an offline-crackable credential in
     * a file the holder is invited to keep.
     *
     * @param email       address on the account
     * @param dateOfBirth as declared
     * @param timeZone    zone local times are read in
     * @param status      where the account stands
     * @param createdAt   when it was opened
     * @param activatedAt when it was activated, or {@code null}
     * @param roles       roles held
     */
    private record HolderEntry(
            String email,
            LocalDate dateOfBirth,
            ZoneId timeZone,
            String status,
            Instant createdAt,
            Instant activatedAt,
            Set<String> roles) {
    }

    /**
     * One consent, as the holder gave it.
     *
     * @param purpose       what was consented to
     * @param grantedBy     in whose name
     * @param grantedAt     when
     * @param revokedAt     when it was withdrawn, or {@code null}
     * @param evidenceCleared whether the evidence has already been removed by an erasure
     */
    private record ConsentEntry(
            String purpose,
            String grantedBy,
            Instant grantedAt,
            Instant revokedAt,
            boolean evidenceCleared) {
    }

    /**
     * One session that is still open.
     *
     * <p>Without its token, which is stored hashed and is a credential rather than data about
     * the holder.
     *
     * @param createdAt  when it was opened
     * @param lastSeenAt when it was last used
     * @param absoluteExpiresAt when it stops working whatever happens
     * @param userAgent  what opened it
     */
    private record SessionEntry(
            Instant createdAt,
            Instant lastSeenAt,
            Instant absoluteExpiresAt,
            String userAgent) {
    }
}
