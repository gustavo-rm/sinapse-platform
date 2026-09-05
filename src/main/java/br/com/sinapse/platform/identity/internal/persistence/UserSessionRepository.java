package br.com.sinapse.platform.identity.internal.persistence;

import br.com.sinapse.platform.identity.internal.domain.UserSession;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Server-side sessions. */
public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

    /**
     * Finds a session by the hash of the token presented with the request.
     *
     * @param tokenHash SHA-256 hash of the presented token
     * @return the session, if the token was ever issued
     */
    Optional<UserSession> findByTokenHash(String tokenHash);

    /**
     * Sessions of a holder that have not been revoked, newest first.
     *
     * <p>Not the same as usable: a session that has gone idle or reached its absolute
     * expiry is still here, and the caller applies the two timeouts. Doing it in the query
     * would put the configured durations into SQL and make them impossible to change
     * without a migration of intent.
     *
     * @param accountId holder
     * @return the sessions that were not explicitly ended
     */
    List<UserSession> findByAccountIdAndRevokedAtIsNullOrderByCreatedAtDesc(UUID accountId);

    /**
     * Finds one session of a holder.
     *
     * @param id        session
     * @param accountId holder it must belong to
     * @return the session, when it is that holder's
     */
    Optional<UserSession> findByIdAndAccountId(UUID id, UUID accountId);
}
