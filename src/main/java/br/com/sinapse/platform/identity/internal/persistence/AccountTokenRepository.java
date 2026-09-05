package br.com.sinapse.platform.identity.internal.persistence;

import br.com.sinapse.platform.identity.internal.domain.AccountToken;
import br.com.sinapse.platform.identity.internal.domain.AccountTokenPurpose;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Single-use tokens delivered to the account holder. */
public interface AccountTokenRepository extends JpaRepository<AccountToken, UUID> {

    /**
     * Finds a token by the hash of the value the holder presented.
     *
     * @param tokenHash SHA-256 hash of the presented value
     * @return the token record, if the value was ever issued
     */
    Optional<AccountToken> findByTokenHash(String tokenHash);

    /**
     * Whether the holder already has a token of this purpose that is neither spent nor
     * expired.
     *
     * <p>Read by the daily majority sweep, which runs every day over the same accounts and
     * must not issue a new reaffirmation token on each run.
     *
     * @param accountId holder
     * @param purpose   purpose
     * @param now       current instant
     * @return whether a usable token is already outstanding
     */
    boolean existsByAccountIdAndPurposeAndConsumedAtIsNullAndExpiresAtAfter(
            UUID accountId, AccountTokenPurpose purpose, Instant now);
}
