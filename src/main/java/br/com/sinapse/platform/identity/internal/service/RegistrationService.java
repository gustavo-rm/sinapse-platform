package br.com.sinapse.platform.identity.internal.service;

import br.com.sinapse.platform.identity.api.ConsentPurpose;
import br.com.sinapse.platform.identity.internal.config.IdentityProperties;
import br.com.sinapse.platform.identity.internal.domain.Account;
import br.com.sinapse.platform.identity.internal.domain.AccountTokenPurpose;
import br.com.sinapse.platform.identity.internal.domain.ConsentEvidence;
import br.com.sinapse.platform.identity.internal.error.EmailAlreadyRegisteredException;
import br.com.sinapse.platform.identity.internal.error.MinorRegistrationNotSupportedException;
import br.com.sinapse.platform.identity.internal.persistence.AccountRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opens an account and records the consents that came with it.
 *
 * <p>Everything happens in one transaction, including the consents. An account that exists
 * without them would be an account that can never be activated and that nobody can explain,
 * and the invariant that binds the two is the one thing section 5.3 asks to be held
 * transactionally.
 *
 * <p>The consents themselves are written by {@code ConsentService} and not here. This
 * service decides that a registration is acceptable; that one decides what a consent may
 * look like.
 */
@Service
public class RegistrationService {

    private final AccountRepository accounts;
    private final ConsentService consents;
    private final AccountTokenService tokens;
    private final PasswordEncoder passwordEncoder;
    private final IdentityProperties properties;
    private final Clock clock;

    /**
     * @param accounts        accounts
     * @param consents        the single writer of consent records
     * @param tokens          issuer of the verification token
     * @param passwordEncoder Argon2id encoder
     * @param properties      configured age threshold
     * @param clock           application clock
     */
    public RegistrationService(AccountRepository accounts, ConsentService consents, AccountTokenService tokens,
            PasswordEncoder passwordEncoder, IdentityProperties properties, Clock clock) {
        this.accounts = accounts;
        this.consents = consents;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Registers an account.
     *
     * <p>The order matters. The age is checked first, so that a refused registration writes
     * nothing at all — not an account, not a consent, not a token, and above all not the
     * date of birth of a child the platform just told to go away.
     *
     * @param command what the holder submitted, already validated for shape
     * @return the account, awaiting verification of its address
     * @throws MinorRegistrationNotSupportedException if the holder is below the threshold
     * @throws EmailAlreadyRegisteredException        if the address is in use
     */
    @Transactional
    public Account register(RegistrationCommand command) {
        Instant now = clock.instant();
        ZoneId zone = command.timeZone();

        if (ageAt(command.dateOfBirth(), now, zone) < properties.consentAgeThreshold()) {
            throw new MinorRegistrationNotSupportedException();
        }
        if (accounts.existsByEmailAndAnonymizedAtIsNull(command.email())) {
            throw new EmailAlreadyRegisteredException();
        }

        Account account = accounts.save(Account.register(
                UUID.randomUUID(),
                command.email(),
                passwordEncoder.encode(command.password()),
                command.dateOfBirth(),
                zone,
                now));

        command.acceptedTerms().forEach((purpose, termsVersionId) ->
                consents.grant(account.id(), purpose, termsVersionId, command.evidence()));

        tokens.issueAndDeliver(account, AccountTokenPurpose.EMAIL_VERIFICATION);
        return account;
    }

    private static int ageAt(LocalDate dateOfBirth, Instant instant, ZoneId zone) {
        return Period.between(dateOfBirth, LocalDate.ofInstant(instant, zone)).getYears();
    }

    /**
     * What a registration submits, once the request has been checked for shape.
     *
     * @param email         address, trimmed
     * @param password      chosen password, in clear, and only until it is hashed
     * @param dateOfBirth   self-declared date of birth
     * @param timeZone      IANA zone the holder's local times are interpreted in
     * @param acceptedTerms wording accepted per purpose, in the order they were submitted
     * @param evidence      what was observed about the act of consenting
     */
    public record RegistrationCommand(
            String email,
            String password,
            LocalDate dateOfBirth,
            ZoneId timeZone,
            Map<ConsentPurpose, UUID> acceptedTerms,
            ConsentEvidence evidence) {

        /** Copies the accepted terms so the command cannot change under the service. */
        public RegistrationCommand {
            acceptedTerms = new LinkedHashMap<>(acceptedTerms);
        }
    }
}
