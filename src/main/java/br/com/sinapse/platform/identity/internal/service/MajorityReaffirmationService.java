package br.com.sinapse.platform.identity.internal.service;

import br.com.sinapse.platform.identity.internal.domain.AccountToken;
import br.com.sinapse.platform.identity.internal.domain.AccountTokenPurpose;
import br.com.sinapse.platform.identity.internal.domain.ConsentEvidence;
import br.com.sinapse.platform.identity.internal.error.InvalidTokenException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reaffirmation of consent by a holder who has reached the age threshold.
 *
 * <p>Two ways in, because there are two ways the holder arrives. Signed in, they answer the
 * request the application shows them; from the message the sweep triggered, they arrive with
 * a token and no session. Both end in the same call, so the rule has one implementation.
 */
@Service
public class MajorityReaffirmationService {

    private final AccountTokenService tokens;
    private final ConsentService consents;

    /**
     * @param tokens   single-use tokens
     * @param consents the single writer of consent records
     */
    public MajorityReaffirmationService(AccountTokenService tokens, ConsentService consents) {
        this.tokens = tokens;
        this.consents = consents;
    }

    /**
     * Reaffirms in the name of the signed-in holder.
     *
     * @param accountId caller
     * @param evidence  what was observed about the act
     */
    @Transactional
    public void reaffirm(UUID accountId, ConsentEvidence evidence) {
        consents.reaffirmMajority(accountId, evidence);
    }

    /**
     * Reaffirms against the token the sweep issued.
     *
     * @param token    value presented by the client
     * @param evidence what was observed about the act
     * @throws InvalidTokenException if the token is unknown, expired or already spent
     */
    @Transactional
    public void reaffirmWithToken(String token, ConsentEvidence evidence) {
        AccountToken consumed = tokens.consume(token, AccountTokenPurpose.MAJORITY_REAFFIRMATION);
        consents.reaffirmMajority(consumed.accountId(), evidence);
    }
}
