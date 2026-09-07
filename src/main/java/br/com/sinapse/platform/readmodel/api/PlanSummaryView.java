package br.com.sinapse.platform.readmodel.api;

import br.com.sinapse.platform.planning.api.PlanStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A plan seen whole: how much of it there is, where it goes, and how much of it happened.
 *
 * <p>Section 3.3 of the API contract.
 *
 * @param planId             the plan
 * @param status             in force, or replaced
 * @param horizonStart       first day it covers, inclusive
 * @param horizonEnd         last day it covers
 * @param createdAt          when it was stored
 * @param supersededByPlanId the plan that replaced it, or {@code null}
 * @param totalPlannedMinutes every scheduled minute in it, superseded or not
 * @param bySubject          the same total broken down, ordered by planned time descending. A
 *                           subject the plan does not touch is absent
 * @param adherence          how much of what has already fallen due was done
 */
@Schema(description = "A study plan summarised: scheduled time, its breakdown, and adherence")
public record PlanSummaryView(
        UUID planId,
        PlanStatus status,
        LocalDate horizonStart,
        LocalDate horizonEnd,
        Instant createdAt,
        UUID supersededByPlanId,
        int totalPlannedMinutes,
        List<SubjectPlan> bySubject,
        Adherence adherence) {

    /** Copies the breakdown, so a summary cannot change after it was produced. */
    public PlanSummaryView {
        bySubject = List.copyOf(bySubject);
    }

    /**
     * What the plan allocates to one subject.
     *
     * @param subjectId      subject
     * @param subjectName    its name, from the catalogue
     * @param plannedMinutes minutes scheduled on it
     * @param sessionCount   how many sessions those minutes are spread over. Carried because
     *                       three hours over two sessions and three hours over eight are
     *                       different plans, and the total alone cannot tell them apart
     */
    @Schema(description = "The share of a plan belonging to one subject")
    public record SubjectPlan(UUID subjectId, String subjectName, int plannedMinutes, int sessionCount) {
    }

    /**
     * How much of the plan that has already fallen due was carried out.
     *
     * <p><strong>Only sessions already past due are counted.</strong> A session scheduled for
     * next Tuesday has not been missed, and counting it as such would make every plan look
     * worse the earlier it is read — which is exactly when a student is most likely to look.
     * Due means the scheduled end has passed, not the scheduled start: a session under way is
     * not yet late.
     *
     * @param plannedElapsed sessions of this plan whose scheduled end is in the past
     * @param executed       how many of those were completed. An abandoned session does not
     *                       count: the student opened it and did not finish, and calling that
     *                       adherence would make the measure agree with itself rather than
     *                       with what happened
     * @param ratio          executed over plannedElapsed, or {@code null} when nothing has
     *                       fallen due yet. Neither zero nor one is an honest stand-in there —
     *                       one reads as a student who followed nothing, the other as a
     *                       perfect record
     */
    @Schema(description = "Adherence over the part of the plan that has already fallen due")
    public record Adherence(int plannedElapsed, int executed, Double ratio) {
    }
}
