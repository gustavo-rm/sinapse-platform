package br.com.sinapse.platform.identity.internal.service;

import br.com.sinapse.platform.identity.api.AccountStatus;
import br.com.sinapse.platform.identity.internal.domain.Account;
import br.com.sinapse.platform.identity.internal.domain.AccountToken;
import br.com.sinapse.platform.identity.internal.domain.AccountTokenPurpose;
import br.com.sinapse.platform.identity.internal.error.InvalidCredentialsException;
import br.com.sinapse.platform.identity.internal.error.InvalidTokenException;
import br.com.sinapse.platform.identity.internal.persistence.AccountRepository;
import br.com.sinapse.platform.shared.web.problem.ApiErrorType;
import br.com.sinapse.platform.shared.web.problem.ApiException;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Setting a new password, either with the old one or with a token.
 *
 * <p>Both routes end the same way: every session of the account is revoked, in the same
 * transaction (ADR 0010). The reason is the case where the password is being changed
 * <em>because</em> someone else knows it — leaving that someone's session open would defeat
 * the change. The session that asked for it goes too; the client authenticates again with
 * the password it just chose.
 *
 * <p>Requesting a reset answers the same way whether the address exists or not. The
 * alternative turns the route into a membership test.
 */
@Service
public class PasswordService {

    private final AccountRepository accounts;
    private final AccountTokenService tokens;
    private final SessionService sessions;
    private final PasswordEncoder passwordEncoder;

    /**
     * @param accounts        accounts
     * @param tokens          single-use tokens
     * @param sessions        sessions, all of which end on a change
     * @param passwordEncoder Argon2id encoder
     */
    public PasswordService(AccountRepository accounts, AccountTokenService tokens, SessionService sessions,
            PasswordEncoder passwordEncoder) {
        this.accounts = accounts;
        this.tokens = tokens;
        this.sessions = sessions;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Issues a reset token, if the address belongs to an account.
     *
     * @param email address submitted
     */
    @Transactional
    public void requestReset(String email) {
        accounts.findByEmailAndAnonymizedAtIsNull(email)
                .filter(account -> account.status() != AccountStatus.ANONYMIZED)
                .ifPresent(account -> tokens.issueAndDeliver(account, AccountTokenPurpose.PASSWORD_RESET));
    }

    /**
     * Sets a new password against a reset token.
     *
     * @param token       value presented by the client
     * @param newPassword password chosen
     * @throws InvalidTokenException if the token is unknown, expired or already spent
     */
    @Transactional
    public void completeReset(String token, String newPassword) {
        AccountToken consumed = tokens.consume(token, AccountTokenPurpose.PASSWORD_RESET);
        Account account = require(consumed.accountId());
        account.changePasswordHash(passwordEncoder.encode(newPassword));
        sessions.revokeAll(account.id());
    }

    /**
     * Changes the password of the caller.
     *
     * @param accountId       caller
     * @param currentPassword password in force
     * @param newPassword     password chosen
     * @throws InvalidCredentialsException if the current password does not match
     */
    @Transactional
    public void change(UUID accountId, String currentPassword, String newPassword) {
        Account account = require(accountId);
        if (!passwordEncoder.matches(currentPassword, account.passwordHash())) {
            throw new InvalidCredentialsException();
        }
        account.changePasswordHash(passwordEncoder.encode(newPassword));
        sessions.revokeAll(accountId);
    }

    private Account require(UUID accountId) {
        return accounts.findById(accountId)
                .orElseThrow(() -> new ApiException(ApiErrorType.RESOURCE_NOT_FOUND));
    }
}
