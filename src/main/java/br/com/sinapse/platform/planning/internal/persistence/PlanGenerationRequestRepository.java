package br.com.sinapse.platform.planning.internal.persistence;

import br.com.sinapse.platform.planning.api.GenerationRequestStatus;
import br.com.sinapse.platform.planning.internal.domain.PlanGenerationRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Plan generation jobs.
 *
 * <p>Only the reads a persisted structure needs. The queue operations — claiming a row with
 * {@code for update skip locked}, recording an attempt, storing the snapshot and the seed —
 * belong to the consumer, which is the next step of the build and is deliberately absent
 * here.
 */
public interface PlanGenerationRequestRepository extends JpaRepository<PlanGenerationRequest, UUID> {

    /**
     * The job an account has that has not finished.
     *
     * <p>At most one, by the partial index: each run costs minutes of CPU, and without the
     * index an impatient student queues dozens.
     *
     * @param accountId student
     * @param statuses  the non-terminal states
     * @return the job, if there is one
     */
    Optional<PlanGenerationRequest> findByAccountIdAndStatusIn(UUID accountId,
            List<GenerationRequestStatus> statuses);
}
