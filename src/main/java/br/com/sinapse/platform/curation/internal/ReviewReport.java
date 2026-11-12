package br.com.sinapse.platform.curation.internal;

import br.com.sinapse.platform.curation.internal.model.DesiredCatalogue;
import br.com.sinapse.platform.curation.internal.model.DesiredEdge;
import br.com.sinapse.platform.curation.internal.model.DesiredReview;
import br.com.sinapse.platform.curation.internal.model.DesiredTopic;
import br.com.sinapse.platform.curation.internal.model.TopicKey;
import br.com.sinapse.platform.curriculum.api.EdgeProvenance;
import br.com.sinapse.platform.curriculum.api.EdgeStrength;
import br.com.sinapse.platform.curriculum.api.EffortTier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * What a person needs to review a catalogue without opening the files.
 *
 * <p>Written for someone who teaches the subject, not for whoever maintains the tool. The point
 * is that they can look at it and say "this topic should have a prerequisite" or "nothing should
 * depend on that one" without reading two hundred rows of CSV, so it shows the shape of the graph
 * rather than its rows: where it starts, where it ends, how deep it goes, and which edges leave
 * the subject.
 *
 * <p>Only from files that validate. A graph with a cycle has no depth and no entry points, and a
 * report that printed numbers for one would be describing something that cannot be imported.
 */
final class ReviewReport {

    private ReviewReport() {
    }

    /**
     * Prints the report.
     *
     * @param desired what the files describe, already validated
     * @param out     where to write
     */
    static void print(DesiredCatalogue desired, Consumer<String> out) {
        Map<TopicKey, DesiredTopic> topics = new LinkedHashMap<>();
        desired.topics().forEach(topic -> topics.put(topic.key(), topic));
        Map<TopicKey, List<TopicKey>> prerequisites = new HashMap<>();
        Map<TopicKey, List<TopicKey>> dependents = new HashMap<>();
        for (DesiredEdge edge : desired.edges()) {
            prerequisites.computeIfAbsent(edge.dependent(), key -> new ArrayList<>()).add(edge.prerequisite());
            dependents.computeIfAbsent(edge.prerequisite(), key -> new ArrayList<>()).add(edge.dependent());
        }

        for (Map.Entry<String, String> subject : desired.subjects().entrySet()) {
            printSubject(subject.getKey(), subject.getValue(), desired, topics, prerequisites,
                    dependents, out);
        }

        long crossing = desired.edges().stream().filter(DesiredEdge::crossesSubjects).count();
        List<TopicKey> longest = longestChain(topics.keySet().stream().toList(), prerequisites,
                key -> true);
        out.accept("  all subjects read: %d topic(s), %d edge(s), %d across subjects".formatted(
                topics.size(), desired.edges().size(), crossing));
        out.accept("    longest prerequisite chain overall: %d topic(s)%s".formatted(
                longest.size(), drawn(longest, TopicKey::toString)));
    }

    private static void printSubject(String code, String name, DesiredCatalogue desired,
            Map<TopicKey, DesiredTopic> topics, Map<TopicKey, List<TopicKey>> prerequisites,
            Map<TopicKey, List<TopicKey>> dependents, Consumer<String> out) {

        List<DesiredTopic> own = topics.values().stream()
                .filter(topic -> topic.key().subjectCode().equals(code))
                .sorted(Comparator.comparingInt(DesiredTopic::position))
                .toList();
        List<DesiredEdge> declared = desired.edges().stream()
                .filter(edge -> edge.declaredIn().equals(code))
                .toList();

        out.accept("  %s  %s".formatted(code, name));
        out.accept("    %d topic(s), %d edge(s) declared in %s/prerequisites.csv"
                .formatted(own.size(), declared.size(), code));
        printEdgeTable(declared, out);
        long unsourced = declared.stream().filter(edge -> !edge.citesASource()).count();
        out.accept("    edges citing no source: %d".formatted(unsourced));
        printReviews(code, desired.reviewsOf(code), declared, out);
        printCrossing(code, desired.edges(), out);

        Predicate<TopicKey> inSubject = key -> key.subjectCode().equals(code);
        List<TopicKey> chain = longestChain(own.stream().map(DesiredTopic::key).toList(),
                prerequisites, inSubject);
        out.accept("    depth: the longest prerequisite chain inside the subject has %d topic(s)%s"
                .formatted(chain.size(), drawn(chain, TopicKey::topicCode)));

        List<DesiredTopic> entries = own.stream()
                .filter(topic -> !prerequisites.containsKey(topic.key()))
                .toList();
        List<DesiredTopic> leaves = own.stream()
                .filter(topic -> !dependents.containsKey(topic.key()))
                .toList();
        printTopics("entry topics, with no prerequisite at all", entries, out);
        printTopics("leaf topics, that nothing depends on", leaves, out);

        Map<EffortTier, Integer> tiers = new EnumMap<>(EffortTier.class);
        for (EffortTier tier : EffortTier.values()) {
            tiers.put(tier, 0);
        }
        own.forEach(topic -> tiers.merge(topic.effortTier(), 1, Integer::sum));
        StringBuilder bands = new StringBuilder("    effort bands:");
        tiers.forEach((tier, count) -> bands.append(' ').append(tier).append(' ').append(count));
        out.accept(bands.toString());
    }

    /** Strength against provenance: the two judgements a reviewer is asked to confirm. */
    private static void printEdgeTable(List<DesiredEdge> edges, Consumer<String> out) {
        StringBuilder header = new StringBuilder("    %-6s".formatted(""));
        for (EdgeProvenance provenance : EdgeProvenance.values()) {
            header.append("%16s".formatted(provenance));
        }
        out.accept(header.append("%8s".formatted("total")).toString());
        for (EdgeStrength strength : EdgeStrength.values()) {
            StringBuilder row = new StringBuilder("    %-6s".formatted(strength));
            for (EdgeProvenance provenance : EdgeProvenance.values()) {
                row.append("%16d".formatted(edges.stream()
                        .filter(edge -> edge.strength() == strength && edge.provenance() == provenance)
                        .count()));
            }
            row.append("%8d".formatted(edges.stream().filter(edge -> edge.strength() == strength).count()));
            out.accept(row.toString());
        }
    }

    private static void printReviews(String code, List<DesiredReview> reviews,
            List<DesiredEdge> declared, Consumer<String> out) {
        if (reviews.isEmpty()) {
            out.accept(("    review: none recorded in %s/reviews.csv. Nobody who teaches the subject"
                    + " has signed this graph, so none of its edges may be CURATED").formatted(code));
            return;
        }
        reviews.forEach(review -> out.accept("    review: %s by %s".formatted(review.reviewedOn(),
                review.reviewer())));
        long proposed = declared.stream()
                .filter(edge -> edge.provenance() == EdgeProvenance.PROPOSED)
                .count();
        if (proposed > 0) {
            out.accept("    still PROPOSED after the review: %d edge(s)".formatted(proposed));
        }
    }

    private static void printCrossing(String code, List<DesiredEdge> edges, Consumer<String> out) {
        List<DesiredEdge> leaving = edges.stream()
                .filter(edge -> edge.crossesSubjects() && edge.prerequisite().subjectCode().equals(code))
                .toList();
        List<DesiredEdge> arriving = edges.stream()
                .filter(edge -> edge.crossesSubjects() && edge.dependent().subjectCode().equals(code))
                .toList();
        out.accept("    edges across subjects: %d leaving, %d arriving".formatted(leaving.size(),
                arriving.size()));
        for (DesiredEdge edge : leaving) {
            out.accept("      %s -> %s  (%s, %s)".formatted(edge.prerequisite(), edge.dependent(),
                    edge.strength(), edge.provenance()));
        }
        for (DesiredEdge edge : arriving) {
            out.accept("      %s -> %s  (%s, %s)".formatted(edge.prerequisite(), edge.dependent(),
                    edge.strength(), edge.provenance()));
        }
    }

    private static void printTopics(String what, List<DesiredTopic> topics, Consumer<String> out) {
        out.accept("    %s: %d".formatted(what, topics.size()));
        topics.forEach(topic -> out.accept("      %3d  %s  %s".formatted(topic.position(),
                topic.key().topicCode(), topic.name())));
    }

    /**
     * The longest chain of prerequisites among the given topics, counted in topics.
     *
     * <p>Only edges whose two ends satisfy {@code inScope} count, so a subject's depth is its own
     * and not borrowed from another subject's chain. Memoised over an acyclic graph, which is why
     * the report runs only on files that validate. Among equally long chains the first one found,
     * walking the topics in the order given, wins, so two runs print the same chain.
     */
    private static List<TopicKey> longestChain(List<TopicKey> topics,
            Map<TopicKey, List<TopicKey>> prerequisites, Predicate<TopicKey> inScope) {

        Map<TopicKey, List<TopicKey>> memo = new HashMap<>();
        List<TopicKey> longest = List.of();
        for (TopicKey topic : topics) {
            List<TopicKey> chain = chainEndingAt(topic, prerequisites, inScope, memo);
            if (chain.size() > longest.size()) {
                longest = chain;
            }
        }
        return longest;
    }

    /**
     * The longest chain ending at a topic, computed bottom-up with an explicit stack: a deep
     * subject must not overflow the call stack of the tool meant to inspect it.
     */
    private static List<TopicKey> chainEndingAt(TopicKey start,
            Map<TopicKey, List<TopicKey>> prerequisites, Predicate<TopicKey> inScope,
            Map<TopicKey, List<TopicKey>> memo) {

        java.util.Deque<TopicKey> stack = new java.util.ArrayDeque<>();
        stack.push(start);
        while (!stack.isEmpty()) {
            TopicKey topic = stack.peek();
            if (memo.containsKey(topic)) {
                stack.pop();
                continue;
            }
            List<TopicKey> before = prerequisites.getOrDefault(topic, List.of()).stream()
                    .filter(inScope)
                    .toList();
            List<TopicKey> pending = before.stream().filter(key -> !memo.containsKey(key)).toList();
            if (!pending.isEmpty()) {
                pending.forEach(stack::push);
                continue;
            }
            List<TopicKey> best = List.of();
            for (TopicKey prerequisite : before) {
                if (memo.get(prerequisite).size() > best.size()) {
                    best = memo.get(prerequisite);
                }
            }
            List<TopicKey> chain = new ArrayList<>(best);
            chain.add(topic);
            memo.put(topic, List.copyOf(chain));
            stack.pop();
        }
        return memo.get(start);
    }

    private static String drawn(List<TopicKey> chain,
            java.util.function.Function<TopicKey, String> label) {
        if (chain.size() < 2) {
            return "";
        }
        return ": " + String.join(" -> ", chain.stream().map(label).toList());
    }
}
