package br.com.sinapse.platform.planning.internal.service;

import br.com.sinapse.platform.planning.api.GenerationRequestStatus;
import br.com.sinapse.platform.planning.api.GenerationRequestView;
import br.com.sinapse.platform.planning.api.PlanGenerationFailure;
import br.com.sinapse.platform.planning.api.PlanningDirectory;
import br.com.sinapse.platform.planning.internal.config.PlanningProperties;
import br.com.sinapse.platform.planning.internal.domain.PlanGenerationRequest;
import br.com.sinapse.platform.planning.internal.domain.StudyPlan;
import br.com.sinapse.platform.planning.internal.error.GenerationAlreadyRunningException;
import br.com.sinapse.platform.planning.internal.error.SetupIncompleteException;
import br.com.sinapse.platform.planning.internal.error.UnknownGenerationRequestException;
import br.com.sinapse.platform.planning.internal.persistence.PlanGenerationRequestRepository;
import br.com.sinapse.platform.planning.internal.persistence.StudyPlanRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The life of a generation job: queuing it, claiming it, and closing it.
 *
 * <p>The state machine lives here because the job is this module's aggregate. What the job
 * <em>does</em> — reading three contexts, assembling a snapshot, calling the core — is not, and
 * lives in the orchestration layer above.
 *
 * <p><strong>Each method is its own transaction, and that is the design.</strong> Claiming
 * holds a row lock until it commits, so the claim has to be short; the call to the core takes
 * minutes and must not be inside it. What the orchestrator does between two of these calls is
 * outside any transaction, which is exactly what a worker holding a database connection open
 * across an HTTP call would get wrong.
 */
@Service
public class GenerationRequestService {

    /** The states a job is in when it has not finished. */
    private static final List<GenerationRequestStatus> UNFINISHED =
            List.of(GenerationRequestStatus.PENDING, GenerationRequestStatus.RUNNING);

    private final PlanGenerationRequestRepository requests;
    private final StudyPlanRepository plans;
    private final PlanningDirectory directory;
    private final PlanningAccess access;
    private final PlanningProperties.Generation configuration;
    private final Clock clock;

    /**
     * @param requests   generation jobs
     * @param plans      plans, to answer which one a finished job produced
     * @param directory  reads of this module, to check there is anything to plan
     * @param access     the access gate of this module
     * @param properties configured limits
     * @param clock      application clock
     */
    public GenerationRequestService(PlanGenerationRequestRepository requests,
            StudyPlanRepository plans, PlanningDirectory directory, PlanningAccess access,
            PlanningProperties properties, Clock clock) {
        this.requests = requests;
        this.plans = plans;
        this.directory = directory;
        this.access = access;
        this.configuration = properties.generation();
        this.clock = clock;
    }

    /**
     * Queues a job.
     *
     * <p>The horizon is configured and is computed here rather than accepted from the caller. A
     * client that could choose it could ask for a year, which is expensive and mostly wrong
     * (decision F3).
     *
     * @param accountId       student
     * @param catalogImportId curated catalogue state in effect, or {@code null}
     * @return the queued job
     * @throws GenerationAlreadyRunningException if the account already has one unfinished
     * @throws SetupIncompleteException          if there is nothing to plan yet
     */
    @Transactional
    public GenerationRequestView queue(UUID accountId, UUID catalogImportId) {
        access.requireProcessable(accountId);
        requireNothingRunning(accountId);

        LocalDate start = LocalDate.now(clock);
        Period horizon = configuration.horizon();
        requireSomethingToPlan(accountId, start, start.plus(horizon));
        PlanGenerationRequest request = new PlanGenerationRequest(UUID.randomUUID(), accountId,
                start, start.plus(horizon), catalogImportId, clock.instant());
        try {
            // Flushed here so that the partial index answers inside this call. Left to the end
            // of the transaction, the violation would surface from the commit, where there is
            // no longer a handler that can turn it into a 409.
            return viewOf(requests.saveAndFlush(request));
        } catch (DataIntegrityViolationException violation) {
            // The only unique constraint that can fail here is one unfinished job per account.
            throw new GenerationAlreadyRunningException();
        }
    }

    /**
     * Takes work from the queue and marks it running.
     *
     * <p>Short by necessity: the rows are locked until this commits.
     *
     * @return the jobs claimed, now running
     */
    @Transactional
    public List<ClaimedJob> claimNext() {
        return requests.claimPending(configuration.retryBackoff().toMillis() / 1000.0,
                        configuration.batchSize()).stream()
                .map(this::claim)
                .toList();
    }

    /**
     * Records what is about to be sent to the core.
     *
     * @param requestId       job
     * @param snapshot        the exact document being sent
     * @param algorithmParams parameters the run will use
     * @param randomSeed      seed the run will use
     */
    @Transactional
    public void recordSubmission(UUID requestId, Map<String, Object> snapshot,
            Map<String, Object> algorithmParams, long randomSeed) {
        require(requestId).recordSubmission(snapshot, algorithmParams, randomSeed);
    }

    /**
     * Closes a job as finished.
     *
     * <p>Called inside the transaction that writes the plan, so that a plan without a finished
     * job, or a finished job without a plan, cannot exist.
     *
     * @param requestId   job
     * @param coreVersion version of the optimiser that ran
     */
    @Transactional
    public void succeed(UUID requestId, String coreVersion) {
        require(requestId).succeed(clock.instant(), coreVersion);
    }

    /**
     * Closes a job as failed, or puts it back in the queue.
     *
     * <p>Which of the two depends on the failure and on how many attempts it has had. Only an
     * unreachable core is worth another attempt: a core that rejected the payload will reject
     * the same payload again, and an internal failure that repeats is a bug rather than weather.
     *
     * @param requestId job
     * @param reason    which kind of failure it was
     * @return whether the job was left pending for another attempt
     */
    @Transactional
    public boolean failOrRetry(UUID requestId, PlanGenerationFailure reason) {
        PlanGenerationRequest request = require(requestId);
        if (reason.isWorthRetrying() && request.attemptCount() < configuration.maxAttempts()) {
            request.releaseForRetry();
            return true;
        }
        request.fail(clock.instant(), reason);
        return false;
    }

    /**
     * A job of the caller.
     *
     * @param accountId student
     * @param requestId job
     * @return the job
     * @throws UnknownGenerationRequestException if there is no such job for this account
     */
    @Transactional(readOnly = true)
    public GenerationRequestView require(UUID accountId, UUID requestId) {
        access.requireProcessable(accountId);
        return requests.findById(requestId)
                .filter(request -> request.accountId().equals(accountId))
                .map(this::viewOf)
                .orElseThrow(UnknownGenerationRequestException::new);
    }

    /**
     * The job an account has that has not finished.
     *
     * @param accountId student
     * @return it, if there is one
     */
    @Transactional(readOnly = true)
    public Optional<GenerationRequestView> unfinishedJobOf(UUID accountId) {
        return requests.findByAccountIdAndStatusIn(accountId, UNFINISHED).map(this::viewOf);
    }

    private ClaimedJob claim(PlanGenerationRequest request) {
        request.claim(clock.instant());
        return new ClaimedJob(request.id(), request.accountId(), request.horizonStart(),
                request.horizonEnd(), request.attemptCount());
    }

    /**
     * Refuses a plan for a student who has not said what to plan.
     *
     * <p>Decision F2: availability and at least one goal, and nothing beyond them, so that
     * activation is not penalised. Both are checked over the horizon this job would plan — a
     * routine that ended last month is not availability for next month, and a plan built from
     * defaults would be fiction presented as a recommendation.
     */
    private void requireSomethingToPlan(UUID accountId, LocalDate from, LocalDate to) {
        boolean hasAvailability = directory.availabilityOf(accountId).stream()
                .anyMatch(window -> window.isEffectiveDuring(from, to));
        if (!hasAvailability || directory.activeGoalsOf(accountId).isEmpty()) {
            throw new SetupIncompleteException();
        }
    }

    private void requireNothingRunning(UUID accountId) {
        requests.findByAccountIdAndStatusIn(accountId, UNFINISHED).ifPresent(running -> {
            throw new GenerationAlreadyRunningException();
        });
    }

    private PlanGenerationRequest require(UUID requestId) {
        return requests.findById(requestId).orElseThrow(UnknownGenerationRequestException::new);
    }

    private GenerationRequestView viewOf(PlanGenerationRequest request) {
        UUID planId = request.status() == GenerationRequestStatus.READY
                ? plans.findByGenerationRequestId(request.id()).map(StudyPlan::id).orElse(null)
                : null;

        return new GenerationRequestView(request.id(), request.status(), request.horizonStart(),
                request.horizonEnd(), request.requestedAt(), request.startedAt(),
                request.finishedAt(), request.attemptCount(),
                request.failureReason() == null
                        ? null
                        : PlanGenerationFailure.valueOf(request.failureReason()),
                planId,
                // The core reports no progress and nothing here invents one (decision F4).
                null);
    }

    /**
     * A job a worker now holds.
     *
     * @param id           identifier
     * @param accountId    student
     * @param horizonStart first day of the horizon, inclusive
     * @param horizonEnd   last day of the horizon
     * @param attemptCount which attempt this is
     */
    public record ClaimedJob(
            UUID id,
            UUID accountId,
            LocalDate horizonStart,
            LocalDate horizonEnd,
            int attemptCount) {
    }
}
