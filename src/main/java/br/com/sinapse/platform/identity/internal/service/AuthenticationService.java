package br.com.sinapse.platform.identity.internal.service;

import br.com.sinapse.platform.identity.api.AccountStatus;
import br.com.sinapse.platform.identity.internal.domain.Account;
import br.com.sinapse.platform.identity.internal.error.InvalidCredentialsException;
import br.com.sinapse.platform.identity.internal.persistence.AccountRepository;
import br.com.sinapse.platform.identity.internal.security.AuthenticatedAccount;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Checks a credential and opens a session for it.
 *
 * <p>Every way of failing produces the same answer. An address nobody registered, a wrong
 * password and a suspended account are indistinguishable from outside, because telling them
 * apart turns this route into a way of finding out who has an account here — and, for the
 * suspended case, into a way of finding out that someone withdrew their consent.
 *
 * <p>The password of an unknown address is still verified against a decoy hash. Without
 * that, the route answers a registered address measurably slower than an unregistered one,
 * and the distinction comes back through the clock.
 */
@Service
public class AuthenticationService {

    private final AccountRepository accounts;
    private final SessionService sessions;
    private final PasswordEncoder passwordEncoder;

    /**
     * Hash of a value nobody knows, produced once at startup and compared against whenever
     * the address is unknown. It is written by the same encoder as a real hash, so the
     * comparison costs the same; nothing ever authenticates against it, because the value it
     * came from is discarded here and never existed anywhere else.
     */
    private final String decoyHash;

    /**
     * @param accounts        accounts
     * @param sessions        session store
     * @param passwordEncoder Argon2id encoder
     */
    public AuthenticationService(AccountRepository accounts, SessionService sessions,
            PasswordEncoder passwordEncoder) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.passwordEncoder = passwordEncoder;
        this.decoyHash = passwordEncoder.encode(SecureTokens.generate());
    }

    /**
     * Authenticates a holder and opens a session.
     *
     * @param email     address submitted
     * @param password  password submitted
     * @param ipAddress address the request came from
     * @param userAgent agent string of the client
     * @return the session and the token to hand to the client
     * @throws InvalidCredentialsException whenever the credential does not open a session,
     *                                     for any reason
     */
    @Transactional
    public SessionService.IssuedSession authenticate(String email, String password, String ipAddress,
            String userAgent) {

        Account account = accounts.findByEmailAndAnonymizedAtIsNull(email).orElse(null);
        boolean matches = passwordEncoder.matches(password,
                account == null ? decoyHash : account.passwordHash());

        if (account == null || !matches || account.status() != AccountStatus.ACTIVE) {
            throw new InvalidCredentialsException();
        }
        return sessions.open(account, ipAddress, userAgent);
    }

    /**
     * Resolves the token presented with a request into the account it acts as.
     *
     * <p>An anonymised account is refused even if a session of it somehow survived, which is
     * the one case the revocation on anonymisation could not have covered: a session opened
     * in the same transaction, by a request already in flight.
     *
     * @param token clear session token
     * @return the principal, or empty when the token no longer authenticates anything
     */
    @Transactional
    public Optional<AuthenticatedAccount> resolve(String token) {
        return sessions.authenticate(token)
                .flatMap(session -> accounts.findById(session.accountId())
                        .filter(account -> account.status() != AccountStatus.ANONYMIZED)
                        .map(account -> new AuthenticatedAccount(
                                account.id(), session.id(), account.roles())));
    }
}
