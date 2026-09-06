package br.com.sinapse.platform.planning.internal.domain;

import br.com.sinapse.platform.planning.internal.error.AvailabilityAlreadyClosedException;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * A recurring weekly window in which the student is available to study.
 *
 * <p><strong>Never overwritten on edit.</strong> Changing a routine means closing the window
 * that stopped applying and opening a new one. The reason is in section 9.1: a plan was
 * generated against the availability that existed when it was generated, and a row edited in
 * place would leave that plan explained by an availability the student no longer has.
 *
 * <p>Start and end are local times with no offset, interpreted in the account's own zone.
 * "Tuesday, seven to nine in the evening" is a statement about a week, not about an instant;
 * pinning an offset to it would make it wrong twice a year, at each end of daylight saving.
 *
 * <p>Two windows on the same weekday whose validity ranges overlap may not also overlap in
 * time. The database does not check it — there is no exclusion constraint on this table — so
 * {@link #overlaps} is where the rule lives, and the service serialises the check per account
 * so that two concurrent declarations cannot both pass it.
 */
@Entity
@Table(name = "study_availability")
public class AvailabilityWindow {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Convert(converter = DayOfWeekConverter.class)
    @Column(name = "day_of_week", nullable = false, updatable = false)
    private DayOfWeek dayOfWeek;

    @Column(name = "start_time", nullable = false, updatable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false, updatable = false)
    private LocalTime endTime;

    @Column(name = "effective_from", nullable = false, updatable = false)
    private LocalDate effectiveFrom;

    /** The one mutable field: closing a window is writing this date. */
    @Column(name = "effective_until")
    private LocalDate effectiveUntil;

    /** For JPA. */
    protected AvailabilityWindow() {
    }

    /**
     * Declares a window.
     *
     * @param id             identifier
     * @param accountId      student
     * @param dayOfWeek      day it recurs on
     * @param startTime      when it opens, in the account's zone
     * @param endTime        when it closes, in the account's zone
     * @param effectiveFrom  first day it applies, inclusive
     * @param effectiveUntil last day it applies, inclusive, or {@code null} for open-ended
     * @throws IllegalArgumentException if the window is empty or inverted at either end. The
     *                                  database refuses both as well; this is what keeps a
     *                                  caller that is not the API from meeting the constraint
     *                                  instead of the rule
     */
    public AvailabilityWindow(UUID id, UUID accountId, DayOfWeek dayOfWeek, LocalTime startTime,
            LocalTime endTime, LocalDate effectiveFrom, LocalDate effectiveUntil) {
        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("a window cannot close before it opens");
        }
        if (effectiveUntil != null && effectiveUntil.isBefore(effectiveFrom)) {
            throw new IllegalArgumentException("a window cannot stop applying before it applied");
        }
        this.id = id;
        this.accountId = accountId;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
        this.effectiveFrom = effectiveFrom;
        this.effectiveUntil = effectiveUntil;
    }

    /** Identifier of the window. */
    public UUID id() {
        return id;
    }

    /** Student the window belongs to. */
    public UUID accountId() {
        return accountId;
    }

    /** Day it recurs on. */
    public DayOfWeek dayOfWeek() {
        return dayOfWeek;
    }

    /** When it opens, in the account's zone. */
    public LocalTime startTime() {
        return startTime;
    }

    /** When it closes, in the account's zone. */
    public LocalTime endTime() {
        return endTime;
    }

    /** First day it applies, inclusive. */
    public LocalDate effectiveFrom() {
        return effectiveFrom;
    }

    /** Last day it applies, inclusive, or {@code null}. */
    public LocalDate effectiveUntil() {
        return effectiveUntil;
    }

    /** Whether the window has been closed. */
    public boolean isClosed() {
        return effectiveUntil != null;
    }

    /**
     * Whether the window was in force on a day.
     *
     * <p>Both ends are inclusive: a window closed on the tenth still applied on the tenth.
     *
     * @param date day being asked about
     * @return whether the validity range covers it
     */
    public boolean isEffectiveOn(LocalDate date) {
        return !date.isBefore(effectiveFrom)
                && (effectiveUntil == null || !date.isAfter(effectiveUntil));
    }

    /**
     * Whether two windows would both apply, on the same weekday, at the same time.
     *
     * <p>Three conditions, all of which have to hold: the same day of the week, validity
     * ranges that meet, and times that meet. Each comparison treats its own ends the way the
     * data does — the validity range is closed at both ends, because a window closed on the
     * tenth applied on the tenth, and the time range is half-open, because a window ending at
     * nine and one starting at nine do not overlap.
     *
     * <p>An open-ended range is treated as reaching forever, which is what a null
     * {@code effectiveUntil} means.
     *
     * @param other window to compare against
     * @return whether the two cannot both stand
     */
    public boolean overlaps(AvailabilityWindow other) {
        return dayOfWeek == other.dayOfWeek
                && validityMeets(other)
                && timeMeets(other);
    }

    /**
     * Closes the window.
     *
     * @param until last day it applies, inclusive. Must not precede the day it opened
     * @throws AvailabilityAlreadyClosedException if it is already closed, because moving the
     *                                            date would rewrite when the routine actually
     *                                            changed
     * @throws IllegalArgumentException           if the date precedes the start of validity
     */
    public void close(LocalDate until) {
        if (effectiveUntil != null) {
            throw new AvailabilityAlreadyClosedException();
        }
        if (until.isBefore(effectiveFrom)) {
            throw new IllegalArgumentException("a window cannot stop applying before it applied");
        }
        this.effectiveUntil = until;
    }

    private boolean validityMeets(AvailabilityWindow other) {
        return !effectiveFrom.isAfter(orForever(other.effectiveUntil))
                && !other.effectiveFrom.isAfter(orForever(effectiveUntil));
    }

    private boolean timeMeets(AvailabilityWindow other) {
        return startTime.isBefore(other.endTime) && other.startTime.isBefore(endTime);
    }

    private static LocalDate orForever(LocalDate effectiveUntil) {
        return effectiveUntil == null ? LocalDate.MAX : effectiveUntil;
    }
}
