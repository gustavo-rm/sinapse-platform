package br.com.sinapse.platform.datarights.internal.persistence;

import br.com.sinapse.platform.datarights.api.ErasureRequestStatus;
import br.com.sinapse.platform.datarights.internal.domain.ErasureRequest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

/** Erasure requests. */
public interface ErasureRequestRepository extends JpaRepository<ErasureRequest, UUID> {

    /**
     * The open request of an account.
     *
     * <p>At most one, by the partial index.
     *
     * @param accountId holder
     * @param status    state being looked for
     * @return the request, if there is one
     */
    Optional<ErasureRequest> findByAccountIdAndStatus(UUID accountId, ErasureRequestStatus status);

    /**
     * Every request an account has made, newest first.
     *
     * @param accountId holder
     * @return their requests
     */
    List<ErasureRequest> findByAccountIdOrderByRequestedAtDesc(UUID accountId);

    /**
     * Requests whose window has elapsed.
     *
     * <p>Oldest first, and capped, so that one pass of the job is bounded whatever has
     * accumulated. {@code ix_erasure_due} is the index this reads.
     *
     * @param status  the open state
     * @param now     current instant
     * @param limit   how many to take in one pass
     * @return the requests due to be carried out
     */
    List<ErasureRequest> findByStatusAndEffectiveAtLessThanEqualOrderByEffectiveAtAsc(
            ErasureRequestStatus status, Instant now, Limit limit);
}
