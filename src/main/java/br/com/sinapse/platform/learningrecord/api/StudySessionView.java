package br.com.sinapse.platform.learningrecord.api;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * An executed study session, as other modules and clients see it.
 *
 * <p>One shape for both kinds of session. A session the student started off their own bat
 * carries {@link SessionSource#SELF_DIRECTED} and a null {@code plannedSessionId}; one taken
 * from the plan carries {@link SessionSource#FROM_PLAN} and the identifier. Nothing else
 * differs, and section 8.1 of the architecture document requires exactly that — a second
 * shape would quietly make off-plan study second-class evidence when it is the same evidence.
 *
 * @param id                     identifier
 * @param accountId              student who studied
 * @param topicId                topic studied
 * @param plannedSessionId       planned session this executed, or {@code null}. A bare
 *                               identifier with no foreign key, by rule R2
 * @param kind                   new ground or going back over it
 * @param source                 from the plan or self-directed
 * @param status                 where the session is in its life
 * @param startedAt              when it started
 * @param endedAt                when it closed, or {@code null} while running
 * @param plannedDurationMinutes what the plan asked for, or {@code null} off-plan
 * @param actualDurationMinutes  what it took, or {@code null} while running
 * @param durationSource         how the duration was arrived at, present exactly when the
 *                               duration is
 * @param recallRating           the student's own judgement, only on a completed session
 */
public record StudySessionView(
        UUID id,
        UUID accountId,
        UUID topicId,
        UUID plannedSessionId,
        SessionKind kind,
        SessionSource source,
        SessionStatus status,
        Instant startedAt,
        Instant endedAt,
        Integer plannedDurationMinutes,
        Integer actualDurationMinutes,
        DurationSource durationSource,
        RecallRating recallRating) {

    /** Whether the session is still running. */
    public boolean isOpen() {
        return status == SessionStatus.IN_PROGRESS;
    }

    /**
     * What the session actually took.
     *
     * <p>Empty while the session is running. It is deliberately not "however long it has
     * been open so far": a duration that grows every time it is read is not evidence, and
     * the value only becomes a fact when the session closes.
     *
     * @return the effective duration of a closed session
     */
    public Optional<Duration> effectiveDuration() {
        return Optional.ofNullable(actualDurationMinutes).map(Duration::ofMinutes);
    }
}
