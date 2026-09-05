package br.com.sinapse.platform.identity.internal.service;

import br.com.sinapse.platform.identity.internal.domain.AccountToken;
import br.com.sinapse.platform.identity.internal.domain.AccountTokenPurpose;
import br.com.sinapse.platform.identity.internal.error.InvalidTokenException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns proof of control over an address into a decision about the account.
 *
 * <p>The decision itself is not taken here: whether verifying the address makes the account
 * active depends on consents, and that belongs to the single service that writes both.
 *
 * <p>The route behind this is a POST and not a GET, even though it is reached from a link
 * in a message. A verification changes state, and the CSRF decision of ADR 0009 holds only
 * while no state-changing operation is reachable by GET. The link opens a page; the page
 * posts the token.
 */
@Service
public class EmailVerificationService {

    private final AccountTokenService tokens;
    private final ConsentService consents;

    /**
     * @param tokens   single-use tokens
     * @param consents the single writer of account status
     */
    public EmailVerificationService(AccountTokenService tokens, ConsentService consents) {
        this.tokens = tokens;
        this.consents = consents;
    }

    /**
     * Verifies an address.
     *
     * @param token value presented by the client
     * @throws InvalidTokenException if the token is unknown, expired or already spent
     */
    @Transactional
    public void verify(String token) {
        AccountToken consumed = tokens.consume(token, AccountTokenPurpose.EMAIL_VERIFICATION);
        consents.activateAfterEmailVerification(consumed.accountId());
    }
}
