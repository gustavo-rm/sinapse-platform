package br.com.sinapse.platform.curation.internal.model;

import java.time.LocalDate;

/**
 * One recorded review of a subject's prerequisite graph, from {@code reviews.csv}.
 *
 * <p>Per subject and not per edge (ADR 0019). The claim a result can make is about a subject's
 * graph — "reviewed by someone who teaches it, on this date" — and a per-edge column filled by
 * dragging a cell down a spreadsheet would look more precise than the review it records.
 *
 * <p>The record is what entitles an edge to {@code CURATED}: an edge of a subject with no review
 * is refused that label. Who reviewed is whatever identifier the project uses for its reviewers;
 * it lives in the files and in git, never in the database.
 *
 * @param subject    subject the review covers
 * @param reviewer   who reviewed, as the project identifies its reviewers
 * @param reviewedOn when
 * @param line       line of {@code reviews.csv} it came from
 */
public record DesiredReview(String subject, String reviewer, LocalDate reviewedOn, int line) {
}
