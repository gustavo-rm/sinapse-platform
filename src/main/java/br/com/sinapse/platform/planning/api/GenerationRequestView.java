package br.com.sinapse.platform.planning.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A plan generation job, as the client polls it.
 *
 * <p>There is no notification in this version: the client asks. That is the consequence ADR
 * 0002 accepted when it made generation asynchronous, and it is why the screen needs an
 * explicit waiting state.
 *
 * @param id            identifier
 * @param status        where the job stands
 * @param horizonStart  first day of the horizon it was asked to plan, inclusive
 * @param horizonEnd    last day of that horizon
 * @param requestedAt   when it was asked for
 * @param startedAt     when a worker last claimed it, or {@code null}
 * @param finishedAt    when it finished, or {@code null}
 * @param attemptCount  how many times it has been attempted
 * @param failureReason which kind of failure ended it, or {@code null}
 * @param planId        the plan it produced, or {@code null} until it is ready
 * @param progress      how far along it is, from 0 to 1, or {@code null} for indeterminate.
 *                      Always {@code null} in this version: the core reports no progress, and
 *                      the field exists so that the client's indeterminate state is part of the
 *                      contract rather than an omission. Nothing invents a percentage
 *                      (decision F4)
 */
public record GenerationRequestView(
        UUID id,
        GenerationRequestStatus status,
        LocalDate horizonStart,
        LocalDate horizonEnd,
        Instant requestedAt,
        Instant startedAt,
        Instant finishedAt,
        int attemptCount,
        PlanGenerationFailure failureReason,
        UUID planId,
        Double progress) {
}
