package br.com.sinapse.platform.identity.internal.service;

import br.com.sinapse.platform.identity.internal.config.IdentityProperties;
import br.com.sinapse.platform.identity.internal.domain.Account;
import br.com.sinapse.platform.identity.internal.domain.UserSession;
import br.com.sinapse.platform.identity.internal.error.SessionNotFoundException;
import br.com.sinapse.platform.identity.internal.persistence.UserSessionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opens, validates and ends server-side sessions.
 *
 * <p>ADR 0010 chose an opaque session over a stateless token for one reason, and this class
 * is where the reason cashes out: {@link #revokeAll(UUID)} is called inside the same
 * transaction that suspends an account, so a revoked consent stops every request that was
 * already in flight behind a valid-looking credential.
 *
 * <p>Longest user agent kept is bounded, because the value comes from the client and its
 * only purpose is to let the holder recognise a session in a list.
 */
@Service
public class SessionService {

    /** Bound on the stored user agent. Beyond this it stops helping anyone recognise anything. */
    private static final int MAX_USER_AGENT = 256;

    private final UserSessionRepository sessions;
    private final IdentityProperties properties;
    private final Clock clock;

    /**
     * @param sessions   session records
     * @param properties configured lifetimes
     * @param clock      application clock
     */
    public SessionService(UserSessionRepository sessions, IdentityProperties properties, Clock clock) {
        this.sessions = sessions;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Opens a session and returns the token the client will present.
     *
     * @param account   holder the session belongs to
     * @param ipAddress address the request came from, as resolved from the infrastructure
     * @param userAgent agent string sent by the client, possibly {@code null}
     * @return the session and its clear token, which is the only time the token exists here
     */
    @Transactional
    public IssuedSession open(Account account, String ipAddress, String userAgent) {
        Instant now = clock.instant();
        String value = SecureTokens.generate();
        UserSession session = new UserSession(
                UUID.randomUUID(),
                account.id(),
                SecureTokens.hash(value),
                now,
                now.plus(properties.session().absoluteTimeout()),
                truncate(userAgent),
                ipAddress == null ? null : SecureTokens.hash(ipAddress));
        sessions.save(session);
        return new IssuedSession(session, value);
    }

    /**
     * Resolves the token presented with a request.
     *
     * <p>Both expiries are applied here rather than in the query, so that changing a
     * configured duration takes effect on the next request instead of requiring the stored
     * rows to be rewritten. A session that is found but no longer usable answers empty: the
     * caller cannot tell an expired token from one that never existed, and neither can
     * whoever sent it.
     *
     * @param value clear token presented
     * @return the session, when it may still authenticate a request
     */
    @Transactional
    public Optional<UserSession> authenticate(String value) {
        Instant now = clock.instant();
        return sessions.findByTokenHash(SecureTokens.hash(value))
                .filter(session -> session.isUsableAt(now, properties.session().idleTimeout()))
                .map(session -> {
                    session.touch(now);
                    return session;
                });
    }

    /**
     * Sessions of a holder that are still usable.
     *
     * @param accountId holder
     * @return the sessions the holder could end
     */
    @Transactional(readOnly = true)
    public List<UserSession> listUsable(UUID accountId) {
        Instant now = clock.instant();
        return sessions.findByAccountIdAndRevokedAtIsNullOrderByCreatedAtDesc(accountId).stream()
                .filter(session -> session.isUsableAt(now, properties.session().idleTimeout()))
                .toList();
    }

    /**
     * Ends one session of a holder.
     *
     * @param accountId holder the session must belong to
     * @param sessionId session to end
     * @throws SessionNotFoundException if the session does not exist or belongs to someone
     *                                  else, which answer the same way on purpose
     */
    @Transactional
    public void terminate(UUID accountId, UUID sessionId) {
        UserSession session = sessions.findByIdAndAccountId(sessionId, accountId)
                .orElseThrow(SessionNotFoundException::new);
        session.revoke(clock.instant());
    }

    /**
     * Ends every session of a holder.
     *
     * <p>Called by the suspension and the anonymisation of an account, inside their
     * transaction, and by every change of password.
     *
     * @param accountId holder
     */
    @Transactional
    public void revokeAll(UUID accountId) {
        Instant now = clock.instant();
        sessions.findByAccountIdAndRevokedAtIsNullOrderByCreatedAtDesc(accountId)
                .forEach(session -> session.revoke(now));
    }

    private static String truncate(String userAgent) {
        if (userAgent == null) {
            return null;
        }
        return userAgent.length() <= MAX_USER_AGENT ? userAgent : userAgent.substring(0, MAX_USER_AGENT);
    }

    /**
     * A session and the token that was handed to the client for it.
     *
     * @param session stored session
     * @param token   clear value; it exists here once and is never recoverable afterwards
     */
    public record IssuedSession(UserSession session, String token) {
    }
}
