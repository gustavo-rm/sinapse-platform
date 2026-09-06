package br.com.sinapse.platform.planning.internal.persistence;

import br.com.sinapse.platform.planning.internal.domain.AvailabilityWindow;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Availability windows. Never deleted: a window that stopped applying is closed, not removed. */
public interface AvailabilityWindowRepository extends JpaRepository<AvailabilityWindow, UUID> {

    /**
     * Every window an account has declared, closed ones included.
     *
     * @param accountId student
     * @return the windows, ordered by day and then by start time
     */
    List<AvailabilityWindow> findByAccountIdOrderByDayOfWeekAscStartTimeAsc(UUID accountId);

    /**
     * The windows of an account on one weekday.
     *
     * <p>Read before a new window is accepted. The whole weekday is loaded rather than a
     * filtered subset, because the overlap rule compares two ranges at once and a query that
     * tried to express it in SQL would be the rule written twice.
     *
     * @param accountId student
     * @param dayOfWeek day being declared
     * @return the windows on that day, closed ones included
     */
    List<AvailabilityWindow> findByAccountIdAndDayOfWeek(UUID accountId, DayOfWeek dayOfWeek);

    /**
     * The windows of an account that were in force on a given day.
     *
     * @param accountId student
     * @param date      day being asked about
     * @return the windows whose validity range covers it
     */
    @Query("""
            select window
              from AvailabilityWindow window
             where window.accountId = :accountId
               and window.effectiveFrom <= :date
               and (window.effectiveUntil is null or window.effectiveUntil >= :date)
             order by window.dayOfWeek, window.startTime
            """)
    List<AvailabilityWindow> findEffectiveOn(@Param("accountId") UUID accountId,
            @Param("date") LocalDate date);

    /**
     * Removes every row of this kind belonging to an account.
     *
     * <p>Only ever called from the erasure transaction. ADR 0011 lists this table among the
     * ones that do not survive.
     *
     * @param accountId student whose data is being erased
     * @return how many rows were removed
     */
    @Modifying
    @Query("delete from AvailabilityWindow window where window.accountId = :accountId")
    int eraseFor(@Param("accountId") UUID accountId);
}
