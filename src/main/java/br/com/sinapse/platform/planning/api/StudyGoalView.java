package br.com.sinapse.platform.planning.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A subject the student intends to get through.
 *
 * <p><strong>A goal names a subject, never a topic.</strong> Every topic of the subject is in
 * scope and the optimisation core decides the order and what fits the horizon — decision L3.
 * Manual exclusion is not offered: for the exam preparation this is built for, the usual
 * scope is the whole syllabus, so an exclusion list would be a feature invented ahead of
 * anyone wanting it.
 *
 * <p>{@code targetDate} is a prioritisation constraint and <strong>not</strong> the plan
 * horizon (decision F3). Preparation can run for a year; generating a year of plan is
 * expensive and mostly wrong, because availability and knowledge change long before that. The
 * date enters the snapshot as pressure, and the horizon stays what it is configured to be.
 *
 * @param subjectId  subject in the curriculum catalogue
 * @param id         identifier
 * @param targetDate when the student would like to be done, or {@code null}
 * @param priority   1 to 5, higher meaning more pressing
 * @param status     where the goal stands
 * @param createdAt  when it was set
 * @param achievedAt when it was closed, or {@code null} while active
 */
public record StudyGoalView(
        UUID id,
        UUID subjectId,
        LocalDate targetDate,
        int priority,
        GoalStatus status,
        Instant createdAt,
        Instant achievedAt) {
}
