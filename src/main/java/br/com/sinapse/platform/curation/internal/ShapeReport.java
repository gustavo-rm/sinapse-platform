package br.com.sinapse.platform.curation.internal;

import br.com.sinapse.platform.curriculum.api.EdgeProvenance;
import br.com.sinapse.platform.curriculum.api.EdgeStrength;
import br.com.sinapse.platform.curriculum.api.EffortTier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Prints an {@link InstanceShape.Shape}.
 *
 * <p>Every number is named by the core's own column name, so it can be read against the core's
 * measurement matrix without translation, and every one carries the matrix's levels beside it:
 * the question this answers is whether a catalogue falls where the core has measured anything.
 */
final class ShapeReport {

    private ShapeReport() {
    }

    /**
     * Prints the shape.
     *
     * @param shape    the computed shape
     * @param settings the configuration it was computed with
     * @param out      where to write
     */
    static void print(InstanceShape.Shape shape, CatalogRunner.ShapeSettings settings,
            Consumer<String> out) {

        out.accept("catalog shape  (definitions: benchmarks/README.md of the core)");
        out.accept("  horizon            %s .. %s, %d day(s), the last one included as the platform"
                .formatted(shape.start(), shape.end(), shape.horizonDays())
                + " assembles it (horizon " + settings.horizon() + ")");
        List<String> goals = new ArrayList<>();
        shape.goals().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(goal -> goals.add(goal.getKey() + " priority " + goal.getValue()));
        out.accept("  goals              " + String.join(", ", goals));

        out.accept("  topics             %d   matrix: %s -> %s".formatted(shape.topics(),
                InstanceShape.levels(InstanceShape.MATRIX_TOPICS),
                InstanceShape.against(shape.topics(), InstanceShape.MATRIX_TOPICS)));

        List<String> bands = new ArrayList<>();
        for (EffortTier tier : EffortTier.values()) {
            bands.add("%s %dm x %d".formatted(tier, settings.bands().get(tier).toMinutes(),
                    shape.perBand().getOrDefault(tier, 0)));
        }
        out.accept("  demanded_minutes   %d   first passes, no per-student adjustment: %s".formatted(
                shape.demandedMinutes(), String.join(", ", bands)));
        out.accept("  available_minutes  %d   %d interval(s) on %d of %d day(s); longest window %d min"
                .formatted(shape.availableMinutes(), shape.intervals(), shape.daysWithWindows(),
                        shape.horizonDays(), shape.longestWindow()));
        out.accept("  tightness          %s   available / demanded; matrix: %s -> %s".formatted(
                decimal(shape.tightness()), InstanceShape.levels(InstanceShape.MATRIX_TIGHTNESS),
                InstanceShape.against(shape.tightness(), InstanceShape.MATRIX_TIGHTNESS)));

        out.accept("  density            %s   %d edge(s) / %d topic(s); matrix: %s -> %s".formatted(
                decimal(shape.density()), shape.edges(), shape.topics(),
                InstanceShape.levels(InstanceShape.MATRIX_DENSITY),
                InstanceShape.against(shape.density(), InstanceShape.MATRIX_DENSITY)));
        out.accept("    prerequisite_edges_hard %d, prerequisite_edges_soft %d; %d edge(s) with an end"
                .formatted(shape.strengths().getOrDefault(EdgeStrength.HARD, 0),
                        shape.strengths().getOrDefault(EdgeStrength.SOFT, 0),
                        shape.edgesOutsideScope())
                + " in a subject without a goal");
        printProvenance(shape, out);
        printImportance(shape, out);

        out.accept("  topics longer than the longest window: %d. They cannot be placed in any"
                .formatted(shape.neverFit().size())
                + " window whole; the core's matrix instances have none");
        shape.neverFit().forEach(topic -> out.accept("    %s  %s  (%s, %d min)".formatted(
                topic.key(), topic.name(), topic.effortTier(),
                settings.bands().get(topic.effortTier()).toMinutes())));
    }

    private static void printProvenance(InstanceShape.Shape shape, Consumer<String> out) {
        List<String> counts = new ArrayList<>();
        for (EdgeProvenance provenance : EdgeProvenance.values()) {
            counts.add(provenance + " " + shape.provenances().getOrDefault(provenance, 0));
        }
        out.accept("  provenance         " + String.join(", ", counts));
        if (shape.provenances().getOrDefault(EdgeProvenance.PROPOSED, 0) > 0) {
            out.accept("    PROPOSED edges travel as CURATED under contract 1.0, so the core's"
                    + " \"curated\" arm would see them (STATE.md K35)");
        }
    }

    /**
     * The input of the core's default importance strategy, {@code goal-priority}: each topic
     * inherits its subject's goal priority. A dispersion of it is not printed because the core's
     * README defines none.
     */
    private static void printImportance(InstanceShape.Shape shape, Consumer<String> out) {
        List<String> levels = new ArrayList<>();
        shape.priorities().forEach((priority, topics) ->
                levels.add("priority %d -> %d topic(s)".formatted(priority, topics)));
        out.accept("  importance         goal-priority input: " + String.join(", ", levels));
        if (shape.priorities().size() == 1) {
            out.accept("    one level only: every topic gets the same importance");
        }
        out.accept("    dispersion: not computed; the core's benchmarks/README.md defines no measure"
                + " of it (importance is held at goal-priority there, not varied)");
    }

    private static String decimal(double value) {
        return Double.isNaN(value) ? "undefined" : String.format(Locale.ROOT, "%.6f", value);
    }
}
