package br.com.sinapse.platform.datarights.internal.service;

import br.com.sinapse.platform.datarights.api.ErasureRequestStatus;
import br.com.sinapse.platform.datarights.api.ErasureRequestView;
import br.com.sinapse.platform.datarights.internal.config.DataRightsProperties;
import br.com.sinapse.platform.datarights.internal.domain.ErasureRequest;
import br.com.sinapse.platform.datarights.internal.error.ErasureAlreadyRequestedException;
import br.com.sinapse.platform.datarights.internal.error.UnknownErasureRequestException;
import br.com.sinapse.platform.datarights.internal.persistence.ErasureRequestRepository;
import br.com.sinapse.platform.identity.api.AccountLifecycle;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The life of an erasure request: making it, withdrawing it, and recording what became of it.
 *
 * <p>Making one suspends the account and revokes its sessions immediately, before the seven days
 * begin. That is not the erasure — nothing is removed yet — but it stops the platform processing
 * the data of somebody who has asked it to stop, from the moment they ask rather than a week
 * later.
 *
 * <p>The holder can still sign in during the window, and can do nothing but withdraw the request
 * and take their data. That is what makes the window reversible: a suspension that locked them
 * out would make the seven days a countdown they could only watch.
 */
@Service
public class ErasureRequestService {

    private final ErasureRequestRepository requests;
    private final AccountLifecycle accounts;
    private final DataRightsProperties properties;
    private final Clock clock;

    /**
     * @param requests   erasure requests
     * @param accounts   the two account transitions this flow needs
     * @param properties configured window and batch size
     * @param clock      application clock
     */
    public ErasureRequestService(ErasureRequestRepository requests, AccountLifecycle accounts,
            DataRightsProperties properties, Clock clock) {
        this.requests = requests;
        this.accounts = accounts;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Requests the erasure of an account.
     *
     * @param accountId holder asking to be erased
     * @return the request, to be withdrawn or waited out
     * @throws ErasureAlreadyRequestedException if one is already open
     */
    @Transactional
    public ErasureRequestView request(UUID accountId) {
        requests.findByAccountIdAndStatus(accountId, ErasureRequestStatus.REQUESTED)
                .ifPresent(open -> {
                    throw new ErasureAlreadyRequestedException();
                });

        Instant now = clock.instant();
        ErasureRequest request = new ErasureRequest(UUID.randomUUID(), accountId, now,
                now.plus(properties.erasureWindow()));
        try {
            // Flushed here so that the partial index answers inside this call rather than from
            // the commit, where no handler can turn it into a 409.
            requests.saveAndFlush(request);
        } catch (DataIntegrityViolationException violation) {
            throw new ErasureAlreadyRequestedException();
        }

        // Immediately, and before the window starts. Processing stops when the holder says so.
        accounts.suspend(accountId);
        return viewOf(request);
    }

    /**
     * Withdraws an open request and puts the account back.
     *
     * @param accountId holder
     * @param requestId request to withdraw
     * @return the withdrawn request
     * @throws UnknownErasureRequestException if there is no such request for this account
     */
    @Transactional
    public ErasureRequestView cancel(UUID accountId, UUID requestId) {
        ErasureRequest request = requireOwned(accountId, requestId);
        request.cancel(clock.instant());
        accounts.reactivate(accountId);
        return viewOf(request);
    }

    /**
     * Records that an attempt failed and nothing was erased.
     *
     * <p>In a transaction of its own, because the attempt's transaction has just rolled back and
     * taken every write with it. Without the new transaction this record would roll back too,
     * and a failed erasure would look exactly like one that had never been attempted.
     *
     * <p>It is not retried on a schedule. Whatever broke will break again, and an erasure looping
     * against a broken module is worse than one that stopped and said so.
     *
     * @param requestId request that failed
     * @param reason    which kind of failure it was, from a closed set and never a message
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID requestId, String reason) {
        requests.findById(requestId).ifPresent(request -> request.fail(reason));
    }

    /**
     * The requests that are due to be carried out.
     *
     * @return the due requests, oldest first, bounded by the configured batch size
     */
    @Transactional(readOnly = true)
    public List<UUID> due() {
        return requests.findByStatusAndEffectiveAtLessThanEqualOrderByEffectiveAtAsc(
                        ErasureRequestStatus.REQUESTED, clock.instant(),
                        Limit.of(properties.batchSize()))
                .stream()
                .map(ErasureRequest::id)
                .toList();
    }

    /**
     * The open request of an account.
     *
     * @param accountId holder
     * @return it, if there is one
     */
    @Transactional(readOnly = true)
    public Optional<ErasureRequestView> openRequestOf(UUID accountId) {
        return requests.findByAccountIdAndStatus(accountId, ErasureRequestStatus.REQUESTED)
                .map(this::viewOf);
    }

    /**
     * Every request an account has made.
     *
     * @param accountId holder
     * @return their requests, newest first
     */
    @Transactional(readOnly = true)
    public List<ErasureRequestView> requestsOf(UUID accountId) {
        return requests.findByAccountIdOrderByRequestedAtDesc(accountId).stream()
                .map(this::viewOf)
                .toList();
    }

    private ErasureRequest requireOwned(UUID accountId, UUID requestId) {
        return requests.findById(requestId)
                .filter(request -> request.accountId().equals(accountId))
                .orElseThrow(UnknownErasureRequestException::new);
    }

    private ErasureRequestView viewOf(ErasureRequest request) {
        return new ErasureRequestView(request.id(), request.status(), request.requestedAt(),
                request.effectiveAt(), request.completedAt(), request.cancelledAt());
    }
}
