package br.com.sinapse.platform.planning.internal.persistence;

import br.com.sinapse.platform.planning.api.GenerationRequestStatus;
import br.com.sinapse.platform.planning.internal.domain.PlanGenerationRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * Every job an account has asked for, newest first.
     *
     * @param accountId student
     * @return their jobs
     */
    List<PlanGenerationRequest> findByAccountIdOrderByRequestedAtDesc(UUID accountId);

    /**
     * Claims work from the queue.
     *
     * <p><strong>{@code for update skip locked} is what makes several workers safe.</strong>
     * Each locks the rows it takes and steps over the ones another worker already holds, so two
     * workers running at the same time never claim the same job — without it they would both
     * read the same pending row, both mark it running, and the student would pay for two
     * optimisation runs to get one plan.
     *
     * <p>The rows stay locked until the transaction commits, so the caller's transaction has to
     * be the short one that marks them running, and not the one that waits for the core.
     *
     * <p>The backoff is computed in the statement because it depends on each row's own attempt
     * count: a job that has failed twice waits four times the base interval before it is
     * eligible again. {@code started_at} is the instant of the last attempt, which is why a
     * released job keeps it.
     *
     * @param backoffSeconds base of the exponential backoff
     * @param batchSize      how many jobs to claim at once
     * @return the claimed rows, locked for the caller's transaction
     */
    @Query(value = """
            select *
              from plan_generation_request
             where status = 'PENDING'
               and (started_at is null
                    or started_at + make_interval(
                           secs => :backoffSeconds * power(2, greatest(attempt_count - 1, 0)))
                       <= now())
             order by requested_at
             limit :batchSize
             for update skip locked
            """, nativeQuery = true)
    List<PlanGenerationRequest> claimPending(@Param("backoffSeconds") double backoffSeconds,
            @Param("batchSize") int batchSize);

    /**
     * Removes every row of this kind belonging to an account.
     *
     * <p>Only ever called from the erasure transaction. ADR 0011 lists this table among the
     * ones that do not survive.
     *
     * @param accountId student whose data is being erased
     * @return how many rows were removed
     */
    @Modifying
    @Query("delete from PlanGenerationRequest request where request.accountId = :accountId")
    int eraseFor(@Param("accountId") UUID accountId);
}
