package br.com.sinapse.platform.coreclient.internal;

import br.com.sinapse.platform.coreclient.api.CoreProtocolException;
import br.com.sinapse.platform.coreclient.api.CoreUnavailableException;
import br.com.sinapse.platform.coreclient.api.SinapseCore;
import br.com.sinapse.platform.coreclient.contract.PlanRequest;
import br.com.sinapse.platform.coreclient.contract.PlanResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * The core over HTTP, with every way that can go wrong turned into one of two failures.
 *
 * <p>ADR 0002 puts the failure modes here rather than at the call site, and there are four of
 * them: the core is not reachable, it does not answer in time, it answers with an error, and
 * it answers with something this contract cannot read. The first three are the same thing to
 * a caller — nothing was produced and trying later is sensible — so they share
 * {@link CoreUnavailableException}. The fourth is different in kind and is
 * {@link CoreProtocolException}: retrying a core that is answering wrongly produces the same
 * wrong answer.
 *
 * <p>A rejection by the core is a protocol failure, not an unavailability. A 4xx says the core
 * did not accept this payload, and the payload will be identical on the next attempt; treating
 * it as retryable would burn the retry budget on a request that cannot succeed.
 *
 * <p><strong>The response is checked before anyone sees it.</strong> A partial plan reaching
 * the database is the failure this class exists to prevent — the sessions of a plan are
 * inserted in one transaction and are immutable afterwards, so a schedule with a missing topic
 * or a duplicated sequence would either abort mid-write or be preserved forever. Checking the
 * seed the core echoes is part of that: a run with a different seed from the one that was sent
 * is not the run this job recorded, and the reproducibility guarantee would be a lie.
 *
 * <p>Nothing here logs the payload or the body it received. The message says what was wrong and
 * at most a status code.
 */
@Component
public class RestSinapseCore implements SinapseCore {

    private final RestClient restClient;
    private final CoreProperties properties;

    /**
     * @param sinapseCoreRestClient the client configured for the core
     * @param properties            address and timeouts
     */
    public RestSinapseCore(RestClient sinapseCoreRestClient, CoreProperties properties) {
        this.restClient = sinapseCoreRestClient;
        this.properties = properties;
    }

    @Override
    public PlanResponse generate(PlanRequest request) {
        PlanResponse response;
        try {
            response = restClient.post()
                    .uri(properties.planPath())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .exchange((httpRequest, httpResponse) -> read(httpResponse));
        } catch (ResourceAccessException unreachable) {
            // Connection refused, host unknown, or the read timeout elapsed. The three are
            // indistinguishable from here and identical in consequence.
            throw new CoreUnavailableException("the core did not answer", unreachable);
        } catch (RestClientException failure) {
            throw new CoreUnavailableException("the call to the core failed", failure);
        }
        return validated(request, response);
    }

    private PlanResponse read(RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse httpResponse)
            throws java.io.IOException {

        HttpStatusCode status = httpResponse.getStatusCode();
        if (status.is4xxClientError()) {
            throw new CoreProtocolException(
                    "the core rejected the request with status " + status.value());
        }
        if (!status.is2xxSuccessful()) {
            throw new CoreUnavailableException(
                    "the core answered with status " + status.value());
        }
        try {
            return httpResponse.bodyTo(PlanResponse.class);
        } catch (RestClientException unreadable) {
            throw new CoreProtocolException("the core's answer could not be read", unreadable);
        }
    }

    /**
     * Refuses an answer that cannot be stored as this request's plan.
     *
     * <p>Twelve invariants, in this order: a body; this contract version; at least one session;
     * a core version; the seed sent; every session with topic, kind and start; every duration
     * positive; no sequence index repeated; every topic one that was sent; every session inside
     * one window; no two sessions overlapping; no gap in the sequence. The horizon is not checked
     * here: it travels as two dates and the windows as instants, and comparing them would mean
     * choosing a time zone the contract does not name. The windows the backend sends lie inside
     * the horizon, so a session inside a window is inside the horizon as the backend means it.
     */
    private static PlanResponse validated(PlanRequest request, PlanResponse response) {
        if (response == null) {
            throw new CoreProtocolException("the core answered with an empty body");
        }
        if (!PlanRequest.VERSION.equals(response.contractVersion())) {
            throw new CoreProtocolException(
                    "the core answered against contract version " + response.contractVersion()
                            + ", and this backend speaks " + PlanRequest.VERSION);
        }
        if (response.sessions().isEmpty()) {
            // Generation is refused before it starts unless the student has availability and at
            // least one goal, so there was something to plan. An empty schedule is the core
            // failing to plan it, and storing it would present that as a plan.
            throw new CoreProtocolException("the core produced no sessions");
        }
        validateMetadata(request, response);
        validateSessions(response);
        validateTopics(request, response);
        validateWindows(request, response);
        validateNoOverlap(response);
        validateContiguousSequence(response);
        return response;
    }

    private static void validateMetadata(PlanRequest request, PlanResponse response) {
        PlanResponse.ExecutionMetadata metadata = response.metadata();
        if (metadata == null || metadata.coreVersion() == null || metadata.coreVersion().isBlank()) {
            throw new CoreProtocolException("the core did not say which version produced the plan");
        }
        if (metadata.randomSeed() != request.randomSeed()) {
            throw new CoreProtocolException("the core ran with a seed other than the one sent");
        }
    }

    private static void validateSessions(PlanResponse response) {
        Set<Integer> sequences = new HashSet<>();
        for (PlanResponse.ScheduledSession session : response.sessions()) {
            if (session.topicId() == null || session.kind() == null
                    || session.scheduledStart() == null) {
                throw new CoreProtocolException("the core returned an incomplete session");
            }
            if (session.durationMinutes() <= 0) {
                throw new CoreProtocolException("the core returned a session of no length");
            }
            if (!sequences.add(session.sequenceIndex())) {
                // The sequence is unique within a plan in the database. Catching it here keeps
                // a constraint violation out of the middle of the write transaction.
                throw new CoreProtocolException("the core repeated a sequence index");
            }
        }
    }

    /** Every session is about a topic this request sent. */
    private static void validateTopics(PlanRequest request, PlanResponse response) {
        Set<UUID> sent = request.topics().stream()
                .map(PlanRequest.Topic::id)
                .collect(Collectors.toSet());
        for (PlanResponse.ScheduledSession session : response.sessions()) {
            if (!sent.contains(session.topicId())) {
                throw violation(session, "its topic was not among the topics sent");
            }
        }
    }

    /**
     * Every session fits, start to end, inside one availability window.
     *
     * <p>The core's own definition ({@code PlanOutputInvariants.fitsAWindow}): some window
     * {@code w} with {@code w.start <= start} and {@code start + duration <= w.end}. Adjacent
     * windows are not merged, so a session that runs across the boundary between two windows
     * that touch is a violation: the student declared two intervals, not one.
     */
    private static void validateWindows(PlanRequest request, PlanResponse response) {
        for (PlanResponse.ScheduledSession session : response.sessions()) {
            Instant start = session.scheduledStart();
            Instant end = endOf(session);
            boolean fits = request.availability().stream().anyMatch(window ->
                    !window.start().isAfter(start) && !end.isAfter(window.end()));
            if (!fits) {
                throw violation(session, "it does not fit inside any availability window");
            }
        }
    }

    /**
     * No two sessions overlap, as half-open intervals: one that ends exactly when the next starts
     * does not overlap it, and two that start at the same instant do.
     */
    private static void validateNoOverlap(PlanResponse response) {
        List<PlanResponse.ScheduledSession> chronological = response.sessions().stream()
                .sorted(Comparator.comparing(PlanResponse.ScheduledSession::scheduledStart)
                        .thenComparingInt(PlanResponse.ScheduledSession::sequenceIndex))
                .toList();
        for (int index = 1; index < chronological.size(); index++) {
            PlanResponse.ScheduledSession previous = chronological.get(index - 1);
            PlanResponse.ScheduledSession next = chronological.get(index);
            if (endOf(previous).isAfter(next.scheduledStart())) {
                throw violation(next, "it overlaps session " + previous.sequenceIndex());
            }
        }
    }

    /**
     * The sequence indices have no gap.
     *
     * <p>Contiguity only. The contract does not say whether the sequence starts at 0 or at 1, so
     * no base is imposed; nor does it say the sequence follows the calendar, so that is not
     * imposed either. Uniqueness was checked before this runs.
     */
    private static void validateContiguousSequence(PlanResponse response) {
        List<PlanResponse.ScheduledSession> bySequence = response.sessions().stream()
                .sorted(Comparator.comparingInt(PlanResponse.ScheduledSession::sequenceIndex))
                .toList();
        for (int index = 1; index < bySequence.size(); index++) {
            int previous = bySequence.get(index - 1).sequenceIndex();
            PlanResponse.ScheduledSession next = bySequence.get(index);
            if (next.sequenceIndex() != previous + 1) {
                throw violation(next, "the sequence jumps to it from " + previous);
            }
        }
    }

    private static Instant endOf(PlanResponse.ScheduledSession session) {
        return session.scheduledStart().plus(Duration.ofMinutes(session.durationMinutes()));
    }

    /**
     * A refusal that names the session and the reason, and nothing else.
     *
     * <p>The sequence index and the topic identifier are enough to find the session in the stored
     * snapshot; neither is personal data, and nothing of the body travels with them.
     */
    private static CoreProtocolException violation(PlanResponse.ScheduledSession session,
            String reason) {
        return new CoreProtocolException("the core returned session " + session.sequenceIndex()
                + " (topic " + session.topicId() + ") that breaks an invariant: " + reason);
    }
}
