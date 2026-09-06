package br.com.sinapse.platform.planning.orchestration;

import br.com.sinapse.platform.coreclient.api.CoreProtocolException;
import br.com.sinapse.platform.coreclient.api.CoreUnavailableException;
import br.com.sinapse.platform.coreclient.api.SinapseCore;
import br.com.sinapse.platform.coreclient.contract.PlanRequest;
import br.com.sinapse.platform.coreclient.contract.PlanResponse;
import br.com.sinapse.platform.planning.api.PlanGenerationFailure;
import br.com.sinapse.platform.planning.internal.service.GenerationRequestService;
import br.com.sinapse.platform.planning.internal.service.GenerationRequestService.ClaimedJob;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Runs one generation job from end to end.
 *
 * <p>Assemble, record, call, write. The order is the whole design:
 *
 * <ol>
 *   <li>the snapshot is built from four contexts and is the only place they meet;</li>
 *   <li>it is stored <em>before</em> the call, together with the parameters and the seed, so
 *       that a run which dies mid-call still leaves the record of what was attempted;</li>
 *   <li>the core is called outside any transaction, because it takes minutes;</li>
 *   <li>the plan and the job's completion are written together, or neither is.</li>
 * </ol>
 *
 * <p><strong>What is stored is what was sent.</strong> The snapshot is the request converted by
 * the application's own mapper — the same mapper the HTTP client writes the body with — and not
 * reassembled from the entities afterwards. Reassembling it later would produce a different
 * document, because availability and history move, and a plan explained by a document it was
 * not generated from is explained by nothing (ADR 0007).
 *
 * <p>Nothing here logs a snapshot, a payload or a response body. A failure is logged as its
 * kind and the job it belonged to.
 */
@Component
public class PlanGenerationOrchestrator {

    private static final Logger LOG = LoggerFactory.getLogger(PlanGenerationOrchestrator.class);

    private static final TypeReference<Map<String, Object>> DOCUMENT = new TypeReference<>() { };

    private final SnapshotAssembler assembler;
    private final SinapseCore core;
    private final GenerationRequestService requests;
    private final GeneratedPlanWriter writer;
    private final SeedSource seeds;
    private final ObjectMapper objectMapper;

    /**
     * @param assembler    builds the snapshot
     * @param core         the optimiser
     * @param requests     the job's state machine
     * @param writer       the one transaction that stores a plan
     * @param seeds        where the seed comes from
     * @param objectMapper the application's mapper, which is also the one the client writes the
     *                     request body with
     */
    public PlanGenerationOrchestrator(SnapshotAssembler assembler, SinapseCore core,
            GenerationRequestService requests, GeneratedPlanWriter writer, SeedSource seeds,
            ObjectMapper objectMapper) {
        this.assembler = assembler;
        this.core = core;
        this.requests = requests;
        this.writer = writer;
        this.seeds = seeds;
        this.objectMapper = objectMapper;
    }

    /**
     * Runs a claimed job.
     *
     * <p>Never throws. Every way this can fail ends with the job recording why, because a job
     * that threw would stay {@code RUNNING} forever and the account's partial index would keep
     * the student from ever asking again.
     *
     * @param job the job, already marked running
     */
    public void run(ClaimedJob job) {
        try {
            PlanRequest request = assembler.assemble(job, seeds.next());
            requests.recordSubmission(job.id(), objectMapper.convertValue(request, DOCUMENT),
                    request.algorithmParams(), request.randomSeed());

            PlanResponse response = core.generate(request);
            writer.store(job, response);
        } catch (NothingToPlanException nothingToPlan) {
            LOG.info("Generation job {} had nothing to plan", job.id());
            requests.failOrRetry(job.id(), PlanGenerationFailure.NOTHING_TO_PLAN);
        } catch (CoreUnavailableException unavailable) {
            LOG.warn("Generation job {} could not reach the core", job.id(), unavailable);
            retryOrGiveUp(job, PlanGenerationFailure.CORE_UNAVAILABLE);
        } catch (CoreProtocolException protocol) {
            LOG.error("Generation job {} got an answer the contract cannot read", job.id(),
                    protocol);
            requests.failOrRetry(job.id(), PlanGenerationFailure.CORE_REJECTED);
        } catch (RuntimeException failure) {
            LOG.error("Generation job {} failed", job.id(), failure);
            requests.failOrRetry(job.id(), PlanGenerationFailure.INTERNAL);
        }
    }

    private void retryOrGiveUp(ClaimedJob job, PlanGenerationFailure reason) {
        if (requests.failOrRetry(job.id(), reason)) {
            LOG.info("Generation job {} will be attempted again", job.id());
        }
    }
}
