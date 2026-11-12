package br.com.sinapse.platform.curation.internal.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The state of the catalogue that the files describe.
 *
 * <p>Declarative: this is what the catalogue should be, not a list of operations. That is what
 * makes an import idempotent, and idempotence is what lets a curator run the thing twice without
 * having to remember whether the first run worked.
 *
 * @param subjects   subject code to display name, in the order the directories were read
 * @param topics     every topic across every subject read
 * @param edges      every edge across every subject read
 * @param reviews    every recorded review of a subject's graph, across every subject read
 * @param problems   everything wrong with the files as they were read, before any semantic check
 */
public record DesiredCatalogue(
        Map<String, String> subjects,
        List<DesiredTopic> topics,
        List<DesiredEdge> edges,
        List<DesiredReview> reviews,
        List<CatalogProblem> problems) {

    /** Copies everything: a desired state that changed under the validator would prove nothing. */
    public DesiredCatalogue {
        subjects = new LinkedHashMap<>(subjects);
        topics = List.copyOf(topics);
        edges = List.copyOf(edges);
        reviews = List.copyOf(reviews);
        problems = List.copyOf(problems);
    }

    /**
     * The recorded reviews of one subject.
     *
     * @param subject subject code
     * @return its reviews, in file order
     */
    public List<DesiredReview> reviewsOf(String subject) {
        return reviews.stream().filter(review -> review.subject().equals(subject)).toList();
    }

    /** Whether anything at all was read. */
    public boolean isEmpty() {
        return subjects.isEmpty();
    }
}
