package br.com.sinapse.platform.curation.internal;

import br.com.sinapse.platform.curation.internal.csv.Csv;
import br.com.sinapse.platform.curation.internal.model.DesiredCatalogue;
import br.com.sinapse.platform.curation.internal.model.DesiredEdge;
import br.com.sinapse.platform.curation.internal.model.DesiredTopic;
import br.com.sinapse.platform.curation.internal.model.TopicKey;
import br.com.sinapse.platform.curriculum.api.EdgeProvenance;
import br.com.sinapse.platform.curriculum.api.EdgeStrength;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Emits textbook-order edges into {@code prerequisites.csv}.
 *
 * <p><strong>Into the file, never into the database.</strong> ADR 0014 is explicit and the
 * reason is the whole argument of the ADR: if seeding wrote edges straight to the catalogue, the
 * file would stop being the complete desired state, and every later import would try to remove
 * what the seeding had added. The file is the source of truth or it is nothing.
 *
 * <p>A table of contents is a valid topological order — not a minimal one, but a real one — so
 * asserting it is the cheapest way to get a graph off the ground, and curation then handles the
 * exceptions. The edges are {@code SOFT} because a book's sequence is a claim about a sensible
 * order rather than about a dependency, and their provenance says where they came from so the
 * ablation can tell them from edges a human asserted.
 *
 * <p>It never touches a pair that already has an edge, whatever that edge's provenance. Running
 * it twice therefore changes nothing the second time, and running it after curation cannot
 * silently overwrite a curated judgement with the order a book happened to print.
 */
public final class TextbookOrderSeeder {

    private TextbookOrderSeeder() {
    }

    /**
     * Writes the missing consecutive-pair edges into a subject's file.
     *
     * @param root    catalogue directory
     * @param subject subject code to seed
     * @param desired the state as the files currently describe it
     * @return what it did
     */
    public static Result seed(Path root, String subject, DesiredCatalogue desired) {
        List<DesiredTopic> ordered = desired.topics().stream()
                .filter(topic -> topic.key().subjectCode().equals(subject))
                .sorted(Comparator.comparingInt(DesiredTopic::position))
                .toList();
        if (ordered.size() < 2) {
            return new Result(0, 0, ordered.size());
        }

        Set<DesiredEdge.Pair> taken = new LinkedHashSet<>();
        desired.edges().forEach(edge -> taken.add(edge.pair()));

        List<DesiredEdge> existing = desired.edges().stream()
                .filter(edge -> edge.prerequisite().subjectCode().equals(subject))
                .toList();

        List<List<String>> rows = new ArrayList<>();
        existing.forEach(edge -> rows.add(row(edge.prerequisite(), edge.dependent(),
                edge.strength(), edge.provenance(), edge.sourceReference())));

        int created = 0;
        int kept = 0;
        for (int index = 0; index < ordered.size() - 1; index++) {
            TopicKey from = ordered.get(index).key();
            TopicKey to = ordered.get(index + 1).key();
            if (taken.contains(new DesiredEdge.Pair(from, to))) {
                kept++;
                continue;
            }
            rows.add(row(from, to, EdgeStrength.SOFT, EdgeProvenance.TEXTBOOK_ORDER,
                    "curricular order of " + subject));
            created++;
        }

        if (created > 0) {
            Csv.write(root.resolve(subject).resolve("prerequisites.csv"),
                    CatalogFiles.EDGE_COLUMNS, rows);
        }
        return new Result(created, kept, ordered.size());
    }

    private static List<String> row(TopicKey from, TopicKey to, EdgeStrength strength,
            EdgeProvenance provenance, String reference) {

        return List.of(from.toString(), to.toString(), strength.name(), provenance.name(),
                reference == null ? "" : reference);
    }

    /**
     * What one seeding run did.
     *
     * @param created consecutive pairs that gained an edge
     * @param kept    consecutive pairs that already had one and were left exactly as they were
     * @param topics  topics the subject has
     */
    public record Result(int created, int kept, int topics) {
    }
}
