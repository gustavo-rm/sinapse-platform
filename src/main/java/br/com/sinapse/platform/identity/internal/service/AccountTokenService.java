package br.com.sinapse.platform.identity.internal.service;

import br.com.sinapse.platform.identity.internal.config.IdentityProperties;
import br.com.sinapse.platform.identity.internal.domain.Account;
import br.com.sinapse.platform.identity.internal.domain.AccountToken;
import br.com.sinapse.platform.identity.internal.domain.AccountTokenPurpose;
import br.com.sinapse.platform.identity.internal.error.InvalidTokenException;
import br.com.sinapse.platform.identity.internal.notification.AccountNotification;
import br.com.sinapse.platform.identity.internal.notification.AccountNotifier;
import br.com.sinapse.platform.identity.internal.persistence.AccountTokenRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues and spends the single-use tokens delivered to the account holder.
 *
 * <p>The clear value never leaves this class other than towards the notifier. Callers ask
 * for a token to be issued and delivered, and get nothing back: a service that received
 * the value could log it, return it in a body, or pass it somewhere it does not belong.
 */
@Service
public class AccountTokenService {

    private final AccountTokenRepository tokens;
    private final AccountNotifier notifier;
    private final IdentityProperties properties;
    private final Clock clock;

    /**
     * @param tokens     token records
     * @param notifier   where a token goes on its way to the holder
     * @param properties configured lifetimes
     * @param clock      application clock
     */
    public AccountTokenService(AccountTokenRepository tokens, AccountNotifier notifier,
            IdentityProperties properties, Clock clock) {
        this.tokens = tokens;
        this.notifier = notifier;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Issues a token and hands it to the notifier.
     *
     * @param account holder the token authorises
     * @param purpose what it authorises
     */
    @Transactional
    public void issueAndDeliver(Account account, AccountTokenPurpose purpose) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(lifetimeOf(purpose));
        String value = SecureTokens.generate();

        tokens.save(new AccountToken(UUID.randomUUID(), account.id(), purpose,
                SecureTokens.hash(value), now, expiresAt));

        notifier.deliver(new AccountNotification(account.id(), account.email(), purpose, value, expiresAt));
    }

    /**
     * Spends a token presented by a client.
     *
     * @param value   clear value presented
     * @param purpose purpose the caller is acting on
     * @return the record, now marked consumed
     * @throws InvalidTokenException if the value was never issued, or was issued for
     *                               another purpose, or is expired, or was already spent
     */
    @Transactional
    public AccountToken consume(String value, AccountTokenPurpose purpose) {
        AccountToken token = tokens.findByTokenHash(SecureTokens.hash(value))
                .orElseThrow(InvalidTokenException::new);
        token.consume(purpose, clock.instant());
        return token;
    }

    /**
     * Whether the holder already has a usable token of this purpose.
     *
     * <p>The daily sweep runs over the same accounts every day; without this it would issue
     * a fresh reaffirmation token on each run and turn one notification into thirty.
     *
     * @param accountId holder
     * @param purpose   purpose
     * @return whether a token is outstanding
     */
    @Transactional(readOnly = true)
    public boolean hasOutstanding(UUID accountId, AccountTokenPurpose purpose) {
        return tokens.existsByAccountIdAndPurposeAndConsumedAtIsNullAndExpiresAtAfter(
                accountId, purpose, clock.instant());
    }

    private Duration lifetimeOf(AccountTokenPurpose purpose) {
        IdentityProperties.Tokens lifetimes = properties.tokens();
        return switch (purpose) {
            case EMAIL_VERIFICATION -> lifetimes.emailVerification();
            case PASSWORD_RESET -> lifetimes.passwordReset();
            case MAJORITY_REAFFIRMATION -> lifetimes.majorityReaffirmation();
        };
    }
}
