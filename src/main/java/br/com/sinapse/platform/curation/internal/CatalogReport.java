package br.com.sinapse.platform.curation.internal;

import br.com.sinapse.platform.curation.internal.model.CatalogProblem;
import br.com.sinapse.platform.curation.internal.model.DesiredEdge;
import br.com.sinapse.platform.curation.internal.model.DesiredTopic;
import br.com.sinapse.platform.curriculum.api.PrerequisiteEdgeView;
import br.com.sinapse.platform.curriculum.api.TopicView;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * What the tool prints.
 *
 * <p>Written for a curator, which decides everything. Grouped by subject. Topics named by code
 * and by name, because a code alone is unreadable in quantity and a name alone is ambiguous.
 * Counts stated plainly. No stack trace, no entity class name, no column name — the same rule
 * the HTTP error contract follows, for the same reason: the reader cannot act on any of it.
 */
public final class CatalogReport {

    private CatalogReport() {
    }

    /**
     * Prints validation problems.
     *
     * @param problems what is wrong
     * @param out      where to write
     */
    public static void problems(List<CatalogProblem> problems, Consumer<String> out) {
        if (problems.isEmpty()) {
            out.accept("  no problems found");
            return;
        }
        out.accept("  %d problem(s):".formatted(problems.size()));
        problems.forEach(problem -> out.accept("    " + problem));
    }

    /**
     * Prints a diff, grouped by subject.
     *
     * @param diff what would change
     * @param out  where to write
     */
    public static void diff(CatalogDiff diff, Consumer<String> out) {
        if (diff.isEmpty()) {
            out.accept("  nothing to change: %d topic(s) and %d edge(s) already match the files"
                    .formatted(diff.topicsUnchanged(), diff.edgesUnchanged()));
            printKept(diff, out);
            return;
        }
        Map<UUID, String> byId = byId(diff);
        for (String subject : diff.subjectsRead()) {
            List<String> lines = linesFor(diff, subject, byId);
            if (lines.isEmpty()) {
                continue;
            }
            out.accept("  " + subject + (diff.newSubjects().contains(subject) ? "  (new subject)" : ""));
            lines.forEach(out);
        }
        out.accept("  totals: topics +%d ~%d =%d, edges +%d ~%d -%d =%d".formatted(
                diff.topicsAdded().size(), diff.topicsChanged().size(), diff.topicsUnchanged(),
                diff.edgesAdded().size(), diff.edgesChanged().size(), diff.edgesRemoved().size(),
                diff.edgesUnchanged()));
        printKept(diff, out);
    }

    private static List<String> linesFor(CatalogDiff diff, String subject, Map<UUID, String> byId) {
        List<String> lines = new ArrayList<>();
        for (DesiredTopic topic : diff.topicsAdded()) {
            if (topic.key().subjectCode().equals(subject)) {
                lines.add("    + topic  %s  %s  (position %d, %s)".formatted(
                        topic.key().topicCode(), topic.name(), topic.position(), topic.effortTier()));
            }
        }
        for (CatalogDiff.TopicChange change : diff.topicsChanged()) {
            if (!change.desired().key().subjectCode().equals(subject)) {
                continue;
            }
            lines.add("    ~ topic  %s  %s".formatted(change.desired().key().topicCode(),
                    describe(change)));
        }
        for (DesiredEdge edge : diff.edgesAdded()) {
            if (edge.prerequisite().subjectCode().equals(subject)) {
                lines.add("    + edge   %s -> %s  (%s, %s)".formatted(edge.prerequisite(),
                        edge.dependent(), edge.strength(), edge.provenance()));
            }
        }
        for (CatalogDiff.EdgeChange change : diff.edgesChanged()) {
            if (!change.desired().prerequisite().subjectCode().equals(subject)) {
                continue;
            }
            lines.add("    ~ edge   %s -> %s  (%s, %s  ->  %s, %s)".formatted(
                    change.desired().prerequisite(), change.desired().dependent(),
                    change.stored().strength(), change.stored().provenance(),
                    change.desired().strength(), change.desired().provenance()));
        }
        for (PrerequisiteEdgeView edge : diff.edgesRemoved()) {
            String from = name(byId, edge.prerequisiteTopicId());
            if (from.startsWith(subject + ":")) {
                lines.add("    - edge   %s -> %s".formatted(from, name(byId, edge.dependentTopicId())));
            }
        }
        return lines;
    }

    private static void printKept(CatalogDiff diff, Consumer<String> out) {
        if (diff.topicsMissing().isEmpty()) {
            return;
        }
        out.accept("  %d topic(s) are in the catalogue and not in the files. They are kept: study"
                .formatted(diff.topicsMissing().size()));
        out.accept("  sessions may reference them, and evidence is not deleted as a side effect of");
        out.accept("  an import. Pass --allow-topic-removal to remove them.");
        diff.topicsMissing().forEach(topic ->
                out.accept("    ? topic  %s  %s".formatted(topic.code(), topic.name())));
    }

    private static String describe(CatalogDiff.TopicChange change) {
        List<String> moved = new ArrayList<>();
        if (!change.stored().name().equals(change.desired().name())) {
            moved.add("name \"%s\" -> \"%s\"".formatted(change.stored().name(), change.desired().name()));
        }
        if (change.stored().position() != change.desired().position()) {
            moved.add("position %d -> %d".formatted(change.stored().position(),
                    change.desired().position()));
        }
        if (change.stored().effortTier() != change.desired().effortTier()) {
            moved.add("effort %s -> %s".formatted(change.stored().effortTier(),
                    change.desired().effortTier()));
        }
        return String.join(", ", moved);
    }

    /** Identifier to natural key, so a removed edge prints as a curator wrote it. */
    private static Map<UUID, String> byId(CatalogDiff diff) {
        Map<UUID, String> byId = new java.util.LinkedHashMap<>();
        diff.stored().forEach((key, topic) -> byId.put(topic.id(), key.toString()));
        return byId;
    }

    private static String name(Map<UUID, String> byId, UUID topicId) {
        return byId.getOrDefault(topicId, "(a topic of another subject)");
    }
}
