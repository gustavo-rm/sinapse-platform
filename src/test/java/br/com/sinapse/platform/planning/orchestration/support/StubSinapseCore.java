package br.com.sinapse.platform.planning.orchestration.support;

import br.com.sinapse.platform.coreclient.api.SinapseCore;
import br.com.sinapse.platform.coreclient.contract.PlanRequest;
import br.com.sinapse.platform.coreclient.contract.PlanResponse;
import br.com.sinapse.platform.coreclient.contract.SessionKind;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * A core that behaves like one, for tests, without being one.
 *
 * <p>The optimiser lives in its own repository and nothing here reimplements it. What this
 * needs to be is <strong>a deterministic function of the request</strong>, because that is what
 * makes the reproducibility claim testable: the same snapshot, parameters and seed have to
 * produce the same plan, and a stub that returned a constant would make the claim pass without
 * meaning anything.
 *
 * <p>So it does the smallest thing that depends on all three: it shuffles the topics with a
 * generator seeded from the request and lays them into the availability slots in order. Change
 * the seed and the plan changes; change nothing and it does not. It is not an optimiser and
 * makes no claim to schedule well.
 *
 * <p>{@code elapsedMillis} is fixed rather than measured, so that two runs of the same input are
 * comparable in full and not merely in the parts that happen not to move.
 */
public class StubSinapseCore implements SinapseCore {

    /** What this stub calls itself, so that the version recorded on a job is a real value. */
    public static final String VERSION = "stub-core-1.0";

    private final AtomicInteger calls = new AtomicInteger();

    private volatile Supplier<RuntimeException> failure;

    private volatile int failuresRemaining;

    @Override
    public PlanResponse generate(PlanRequest request) {
        calls.incrementAndGet();
        Supplier<RuntimeException> configured = failure;
        if (configured != null && failuresRemaining != 0) {
            if (failuresRemaining > 0) {
                failuresRemaining--;
            }
            throw configured.get();
        }
        return plan(request);
    }

    /**
     * Makes every call fail until told otherwise.
     *
     * @param failure what to throw
     */
    public void alwaysFail(Supplier<RuntimeException> failure) {
        this.failure = failure;
        this.failuresRemaining = -1;
    }

    /**
     * Makes the next few calls fail and the ones after that succeed.
     *
     * @param times   how many calls to fail
     * @param failure what to throw
     */
    public void failNext(int times, Supplier<RuntimeException> failure) {
        this.failure = failure;
        this.failuresRemaining = times;
    }

    /** Stops failing. */
    public void succeed() {
        this.failure = null;
        this.failuresRemaining = 0;
    }

    /** How many times the core has been called. */
    public int callCount() {
        return calls.get();
    }

    /** Forgets the call count and any configured failure. */
    public void reset() {
        calls.set(0);
        succeed();
    }

    private static PlanResponse plan(PlanRequest request) {
        List<PlanRequest.Topic> topics = new ArrayList<>(request.topics());
        // The one place the seed is used. Seeded from the request, so the same request produces
        // the same order and a different seed produces a different one.
        Collections.shuffle(topics, new Random(request.randomSeed()));

        Set<UUID> alreadyStudied = request.history().stream()
                .filter(topic -> topic.sessionCount() > 0)
                .map(PlanRequest.TopicHistory::topicId)
                .collect(Collectors.toSet());

        List<PlanRequest.AvailabilitySlot> slots = request.availability();
        List<PlanResponse.ScheduledSession> sessions = new ArrayList<>();
        int placed = Math.min(topics.size(), slots.size());
        for (int index = 0; index < placed; index++) {
            PlanRequest.Topic topic = topics.get(index);
            PlanRequest.AvailabilitySlot slot = slots.get(index);
            long slotMinutes = Duration.between(slot.start(), slot.end()).toMinutes();
            sessions.add(new PlanResponse.ScheduledSession(
                    topic.id(),
                    alreadyStudied.contains(topic.id()) ? SessionKind.REVISION : SessionKind.STUDY,
                    slot.start(),
                    (int) Math.min(topic.estimatedMinutes(), slotMinutes),
                    index));
        }

        return new PlanResponse(PlanRequest.VERSION, sessions,
                Map.of("coverage", topics.isEmpty() ? 0.0 : (double) placed / topics.size(),
                        "slotsUsed", placed),
                new PlanResponse.ExecutionMetadata(VERSION, request.randomSeed(),
                        generationsOf(request), 0));
    }

    private static int generationsOf(PlanRequest request) {
        Object generations = request.algorithmParams().get("generations");
        return generations instanceof Number number ? number.intValue() : 0;
    }
}
