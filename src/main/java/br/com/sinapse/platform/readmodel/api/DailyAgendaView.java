package br.com.sinapse.platform.readmodel.api;

import br.com.sinapse.platform.learningrecord.api.DurationSource;
import br.com.sinapse.platform.learningrecord.api.RecallRating;
import br.com.sinapse.platform.learningrecord.api.SessionStatus;
import br.com.sinapse.platform.planning.api.PlannedSessionKind;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The day's agenda: what was planned, and what was done about it.
 *
 * <p>Section 3.2 of the API contract calls this the main argument against deriving the
 * contract from the aggregates, and it is: one screen, four modules. Without it the client
 * would make five calls and reimplement in the browser the matching between a planned session
 * and the session that executed it — which is a rule, and rules do not belong there.
 *
 * <p>Days are grouped in the <em>holder's</em> zone, not the server's. A session scheduled at
 * half past eleven at night belongs to that night wherever the student is, and grouping in
 * UTC would put it on the following day for anyone west of Greenwich.
 *
 * @param days one entry per day in the requested window that has something scheduled, earliest
 *             first. A day with nothing planned is absent rather than present and empty: the
 *             client is drawing a calendar it already knows the shape of
 */
@Schema(description = "Planned sessions of a window, grouped by day, each with its execution")
public record DailyAgendaView(List<Day> days) {

    /** Copies the list, so an agenda cannot change after it was produced. */
    public DailyAgendaView {
        days = List.copyOf(days);
    }

    /**
     * One day of the agenda.
     *
     * @param date    the day, in the holder's own zone
     * @param entries what is scheduled on it, earliest first
     */
    @Schema(description = "One day and what is scheduled on it")
    public record Day(LocalDate date, List<Entry> entries) {

        /** Copies the list. */
        public Day {
            entries = List.copyOf(entries);
        }
    }

    /**
     * One planned session, with the topic and subject named and the execution attached.
     *
     * @param plannedSessionId       the scheduled session
     * @param topicId                topic to be studied
     * @param topicName              its name, from the catalogue
     * @param subjectId              subject the topic belongs to
     * @param subjectName            its name, from the catalogue
     * @param kind                   new ground or going back over it
     * @param scheduledStart         when the core placed it
     * @param plannedDurationMinutes how long it allowed for it
     * @param execution              what the student actually did, or {@code null} if nothing
     */
    @Schema(description = "A scheduled session and the session that executed it, if any")
    public record Entry(
            UUID plannedSessionId,
            UUID topicId,
            String topicName,
            UUID subjectId,
            String subjectName,
            PlannedSessionKind kind,
            Instant scheduledStart,
            int plannedDurationMinutes,
            Execution execution) {
    }

    /**
     * The attempt that stands against a scheduled slot.
     *
     * <p>The most recent one. A session abandoned and then picked up again shows here as the
     * attempt that stands; the ones before it are history, and history is read from the
     * history endpoint.
     *
     * @param sessionId             executed session
     * @param status                where it ended up
     * @param actualDurationMinutes what it took, or {@code null} while it is still running
     * @param recallRating          the student's own judgement, only on a completed session
     * @param durationSource        how the duration was arrived at, present exactly when the
     *                              duration is
     */
    @Schema(description = "The most recent attempt at a scheduled session")
    public record Execution(
            UUID sessionId,
            SessionStatus status,
            Integer actualDurationMinutes,
            RecallRating recallRating,
            DurationSource durationSource) {
    }
}
