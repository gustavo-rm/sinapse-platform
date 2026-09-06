package br.com.sinapse.platform.datarights.api;

import java.time.Instant;
import java.util.UUID;

/**
 * An erasure request, as the holder sees it.
 *
 * <p>It carries no copy of what is to be erased and no e-mail, and neither does the row behind
 * it. Storing either would reintroduce exactly what the request removes, and would do it in the
 * one table designed to outlive the erasure.
 *
 * @param id          identifier
 * @param status      where the request stands
 * @param requestedAt when it was made
 * @param effectiveAt when it takes effect, and until when it can be withdrawn
 * @param completedAt when it was carried out, or {@code null}
 * @param cancelledAt when it was withdrawn, or {@code null}
 */
public record ErasureRequestView(
        UUID id,
        ErasureRequestStatus status,
        Instant requestedAt,
        Instant effectiveAt,
        Instant completedAt,
        Instant cancelledAt) {
}
