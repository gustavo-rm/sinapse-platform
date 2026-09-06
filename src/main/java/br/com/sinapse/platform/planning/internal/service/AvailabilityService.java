package br.com.sinapse.platform.planning.internal.service;

import br.com.sinapse.platform.planning.api.AvailabilityWindowView;
import br.com.sinapse.platform.planning.internal.domain.AvailabilityWindow;
import br.com.sinapse.platform.planning.internal.error.OverlappingAvailabilityException;
import br.com.sinapse.platform.planning.internal.error.UnknownAvailabilityWindowException;
import br.com.sinapse.platform.planning.internal.persistence.AvailabilityWindowRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Declaring and closing the weekly windows a student is available to study in.
 *
 * <p><strong>Editing is closing and declaring again.</strong> There is no method that changes
 * the day or the times of an existing window, and that is the point of section 9.1: a plan was
 * generated against the availability that existed when it was generated, and a row edited in
 * place would leave that plan explained by an availability the student no longer has.
 *
 * <p><strong>The overlap rule and how it is held.</strong> Two windows on the same weekday
 * whose validity ranges meet may not also meet in time — otherwise whatever allocates study
 * hours counts the same hour twice and the plan is built on time that does not exist. There is
 * no exclusion constraint on the table, so the check is here, which the prompt for this module
 * requires and which leaves one problem: under read committed two concurrent declarations do
 * not see each other's uncommitted rows, so each passes the check in isolation while together
 * they overlap.
 *
 * <p>The answer is the one the curriculum already uses for its acyclicity trigger: take
 * {@code pg_advisory_xact_lock} before reading, keyed by the account, so that declarations for
 * one student serialise. It needs no schema change, it holds for the transaction, and it costs
 * nothing — a student declares their week a handful of times, not continuously. Two accounts
 * whose keys happen to collide simply wait for each other, which is harmless.
 */
@Service
public class AvailabilityService {

    /** Namespace of the lock, so that it cannot collide with another feature's key space. */
    private static final String LOCK_NAMESPACE = "study_availability";

    private static final String TAKE_LOCK =
            "select pg_advisory_xact_lock(hashtext(?), hashtext(?))";

    private final AvailabilityWindowRepository windows;
    private final PlanningAccess access;
    private final JdbcTemplate jdbc;

    /**
     * @param windows availability windows
     * @param access  the access gate of this module
     * @param jdbc    template over the application datasource, used only for the advisory lock
     */
    public AvailabilityService(AvailabilityWindowRepository windows, PlanningAccess access,
            JdbcTemplate jdbc) {
        this.windows = windows;
        this.access = access;
        this.jdbc = jdbc;
    }

    /**
     * Declares a window.
     *
     * @param accountId      student
     * @param dayOfWeek      day it recurs on
     * @param startTime      when it opens, in the account's zone
     * @param endTime        when it closes, in the account's zone
     * @param effectiveFrom  first day it applies, inclusive
     * @param effectiveUntil last day it applies, inclusive, or {@code null} for open-ended
     * @return the window
     * @throws OverlappingAvailabilityException if it would overlap one the student already has
     */
    @Transactional
    public AvailabilityWindowView declare(UUID accountId, DayOfWeek dayOfWeek, LocalTime startTime,
            LocalTime endTime, LocalDate effectiveFrom, LocalDate effectiveUntil) {

        access.requireProcessable(accountId);
        serialisePerAccount(accountId);

        AvailabilityWindow candidate = new AvailabilityWindow(UUID.randomUUID(), accountId,
                dayOfWeek, startTime, endTime, effectiveFrom, effectiveUntil);
        requireNoOverlap(candidate);

        return PlanningViews.of(windows.save(candidate));
    }

    /**
     * Closes a window.
     *
     * <p>The date may be in the past. A student saying their Tuesday evening stopped last
     * month is stating what happened, and the row keeps saying what it said for the period it
     * covered — closing only ever shrinks a validity range, so it cannot create an overlap.
     *
     * @param accountId      student
     * @param windowId       window to close
     * @param effectiveUntil last day it applies, inclusive
     * @return the closed window
     * @throws UnknownAvailabilityWindowException if there is no such window for this account
     */
    @Transactional
    public AvailabilityWindowView close(UUID accountId, UUID windowId, LocalDate effectiveUntil) {
        access.requireProcessable(accountId);
        AvailabilityWindow window = requireOwned(accountId, windowId);
        window.close(effectiveUntil);
        return PlanningViews.of(window);
    }

    private void requireNoOverlap(AvailabilityWindow candidate) {
        List<AvailabilityWindow> sameDay = windows.findByAccountIdAndDayOfWeek(
                candidate.accountId(), candidate.dayOfWeek());

        if (sameDay.stream().anyMatch(candidate::overlaps)) {
            throw new OverlappingAvailabilityException();
        }
    }

    private AvailabilityWindow requireOwned(UUID accountId, UUID windowId) {
        return windows.findById(windowId)
                .filter(window -> window.accountId().equals(accountId))
                .orElseThrow(UnknownAvailabilityWindowException::new);
    }

    /**
     * Serialises availability writes for one account, for the rest of the transaction.
     *
     * <p>Not defensive. Without it the overlap check is a read followed by a write, and two
     * transactions doing that at the same time both read a table that does not yet contain the
     * other's row.
     */
    private void serialisePerAccount(UUID accountId) {
        // The function returns void, so the result is discarded; what matters is that the
        // statement ran on this transaction's connection.
        jdbc.query(TAKE_LOCK, resultSet -> null, LOCK_NAMESPACE, accountId.toString());
    }
}
