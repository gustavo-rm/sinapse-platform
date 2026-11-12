package br.com.sinapse.platform.curation;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.sinapse.platform.curation.internal.CatalogCommand;
import br.com.sinapse.platform.curation.internal.CatalogRunner;
import br.com.sinapse.platform.curriculum.api.EffortTier;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * {@code catalog shape}: the instance a catalogue would produce, measured in the core's terms.
 *
 * <p>Two kinds of evidence. Synthetic catalogues pin each definition. And the example catalogue,
 * read but not touched, must reproduce the numbers of the three real-catalogue instances the
 * platform already exported for the core — which were produced by the production assembly path,
 * so agreeing with them is agreeing with what the platform actually sends.
 */
class InstanceShapeCommandTest {

    /** The bands as {@code application.yml} configures them today. */
    private static final Map<EffortTier, Duration> BANDS = Map.of(
            EffortTier.SHORT, Duration.ofMinutes(25), EffortTier.STANDARD, Duration.ofMinutes(50),
            EffortTier.LONG, Duration.ofMinutes(90), EffortTier.EXTENDED, Duration.ofMinutes(150));

    /** A Monday, so the inclusive four-week horizon has five Mondays and one of every other day. */
    private static final Clock MONDAY = Clock.fixed(Instant.parse("2026-10-12T12:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path root;

    private final List<String> printed = new ArrayList<>();

    /**
     * The three instances of {@code src/test/resources/instances/README.md}, recomputed from the
     * catalogue and the declared availability: same demand, same availability, same ratio.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "TUE,THU 19:30-20:30; SAT 09:00-10:10                        | 760  | 0.350230",
        "MON-FRI 19:00-20:00; SAT 09:00-10:30                        | 1620 | 0.746544",
        "MON-FRI 18:30-20:30; SAT 09:00-11:00; SUN 14:00-15:00       | 3240 | 1.493088"})
    void theExampleCatalogueReproducesTheExportedInstances(String availability, long minutes,
            String tightness) {

        int exit = new CatalogRunner(null, null, Path.of("catalog"), printed::add, settings())
                .run(shape(availability, "MED-ANAT:2", "2026-10-12"));

        assertThat(exit).isZero();
        assertThat(printed).contains(
                "  horizon            2026-10-12 .. 2026-11-09, 29 day(s), the last one included as"
                        + " the platform assembles it (horizon P28D)",
                "  goals              MED-ANAT priority 2");
        assertThat(line("  topics")).startsWith("  topics             35 ");
        assertThat(line("  demanded_minutes")).startsWith("  demanded_minutes   2170 ");
        assertThat(line("  available_minutes")).startsWith("  available_minutes  " + minutes + " ");
        assertThat(line("  tightness")).startsWith("  tightness          " + tightness + " ");
        assertThat(line("  density")).startsWith("  density            1.000000   35 edge(s) / 35 topic(s)");
        assertThat(line("    prerequisite_edges_hard")).contains("hard 24", "soft 11");
        assertThat(line("  provenance")).contains("PROPOSED 35", "CURATED 0");
        assertThat(printed).contains("    one level only: every topic gets the same importance");
    }

    @Test
    void eachDefinitionOnASyntheticCatalogueOfTwoSubjects() {
        write("SYN-A", """
                code,name,position,effort_tier
                T01,Topic 01,1,SHORT
                T02,Topic 02,2,EXTENDED
                """, """
                prerequisite,dependent,strength,provenance,source_reference
                SYN-A:T01,SYN-A:T02,HARD,PROPOSED,
                SYN-A:T02,SYN-B:T01,SOFT,TEXTBOOK_ORDER,
                """);
        write("SYN-B", """
                code,name,position,effort_tier
                T01,Topic 01,1,LONG
                """, "prerequisite,dependent,strength,provenance,source_reference\n");
        write("SYN-C", """
                code,name,position,effort_tier
                T01,Topic 01,1,LONG
                """, "prerequisite,dependent,strength,provenance,source_reference\n");

        int exit = runner().run(shape("SAT 09:00-10:00", "SYN-A:5,SYN-B:3", null));

        assertThat(exit).isZero();
        assertThat(line("  horizon")).as("the default first day is today, in the application's zone")
                .startsWith("  horizon            2026-10-12 .. 2026-11-09");
        assertThat(line("  goals")).isEqualTo("  goals              SYN-A priority 5, SYN-B priority 3");
        assertThat(line("  topics")).isEqualTo("  topics             3   matrix: 10, 25 -> below the measured range");
        assertThat(line("  demanded_minutes"))
                .startsWith("  demanded_minutes   265   first passes, no per-student adjustment:")
                .contains("SHORT 25m x 1", "LONG 90m x 1", "EXTENDED 150m x 1");
        assertThat(line("  available_minutes"))
                .isEqualTo("  available_minutes  240   4 interval(s) on 4 of 29 day(s); longest window 60 min");
        assertThat(line("  tightness")).startsWith("  tightness          0.905660   available / demanded;")
                .endsWith("-> inside the measured range");
        assertThat(line("  density")).isEqualTo(
                "  density            0.666667   2 edge(s) / 3 topic(s); matrix: 0.4, 1.2 -> inside the measured range");
        assertThat(line("    prerequisite_edges_hard")).isEqualTo(
                "    prerequisite_edges_hard 1, prerequisite_edges_soft 1; 0 edge(s) with an end in a subject without a goal");
        assertThat(line("  importance")).isEqualTo(
                "  importance         goal-priority input: priority 5 -> 2 topic(s), priority 3 -> 1 topic(s)");
        assertThat(printed).doesNotContain("    one level only: every topic gets the same importance");
        assertThat(line("  topics longer than the longest window")).contains(": 2.");
        assertThat(String.join("\n", printed)).contains("SYN-A:T02  Topic 02  (EXTENDED, 150 min)",
                "SYN-B:T01  Topic 01  (LONG, 90 min)", "dispersion: not computed");
    }

    @Test
    void anEdgeLeavingTheSubjectsPursuedIsSentAndCountedApart() {
        write("SYN-A", """
                code,name,position,effort_tier
                T01,Topic 01,1,SHORT
                """, """
                prerequisite,dependent,strength,provenance,source_reference
                SYN-A:T01,SYN-B:T01,HARD,PROPOSED,
                """);
        write("SYN-B", """
                code,name,position,effort_tier
                T01,Topic 01,1,SHORT
                """, "prerequisite,dependent,strength,provenance,source_reference\n");

        assertThat(runner().run(shape("MON 10:00-11:00", "SYN-A:1", "2026-10-12"))).isZero();

        assertThat(line("  density")).startsWith("  density            1.000000   1 edge(s) / 1 topic(s)");
        assertThat(line("    prerequisite_edges_hard")).contains("1 edge(s) with an end in a subject without a goal");
        assertThat(line("  topics longer than the longest window")).contains(": 0.");
        assertThat(printed).contains("    PROPOSED edges travel as CURATED under contract 1.0, so the"
                + " core's \"curated\" arm would see them (STATE.md K35)");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "                            | SYN-A:1   |            | --availability is required",
        "MON 19:00                   | SYN-A:1   |            | cannot read availability entry \"MON 19:00\"",
        "XYZ 19:00-20:00             | SYN-A:1   |            | days are MON … SUN",
        "FRI-MON 19:00-20:00         | SYN-A:1   |            | days are MON … SUN",
        "MON,FUN 19:00-20:00         | SYN-A:1   |            | days are MON … SUN",
        "MON 20:00-19:00             | SYN-A:1   |            | ending after they start",
        "MON 19:00-25:00             | SYN-A:1   |            | HH:MM-HH:MM",
        "MON 19:00-20:00-21:00       | SYN-A:1   |            | HH:MM-HH:MM",
        "MON 19:00-20:00             |           |            | --goals is required",
        "MON 19:00-20:00             | SYN-A     |            | cannot read goal \"SYN-A\"",
        "MON 19:00-20:00             | SYN-A:9   |            | priority from 1 to 5",
        "MON 19:00-20:00             | SYN-A:x   |            | priority from 1 to 5",
        "MON 19:00-20:00             | :3        |            | cannot read goal",
        "MON 19:00-20:00             | SYN-A:1   | 12/10/2026 | cannot read --start \"12/10/2026\""})
    void anUnreadableOptionIsRefusedWithWhatToWrite(String availability, String goals, String start,
            String message) {
        write("SYN-A", "code,name,position,effort_tier\nT01,Topic 01,1,SHORT\n", null);

        assertThat(runner().run(shape(availability, goals, start))).isEqualTo(2);
        assertThat(printed).anySatisfy(line -> assertThat(line).startsWith("catalog shape  ").contains(message));
    }

    @Test
    void aGoalOnASubjectThatIsNotInTheCatalogueIsRefused() {
        write("SYN-A", "code,name,position,effort_tier\nT01,Topic 01,1,SHORT\n", null);

        assertThat(runner().run(shape("MON 19:00-20:00", "SYN-Z:3", null))).isEqualTo(2);
        assertThat(printed).containsExactly(
                "catalog shape  goal names subject SYN-Z, which is not in the catalogue read");
    }

    @Test
    void twoGoalsOnOneSubjectCollapseToTheHigherPriorityAsInTheCore() {
        write("SYN-A", "code,name,position,effort_tier\nT01,Topic 01,1,SHORT\n", null);

        assertThat(runner().run(shape("MON-WED 19:00-20:00; MON 07:00-07:30", "SYN-A:2,SYN-A:4", null))).isZero();
        assertThat(line("  goals")).isEqualTo("  goals              SYN-A priority 4");
        assertThat(line("  available_minutes")).startsWith("  available_minutes  930   18 interval(s) on 13 of 29 day(s)");
    }

    @Test
    void aCatalogueThatDoesNotValidateHasNoShape() {
        write("SYN-A", "code,name,position,effort_tier\nT01,Topic 01,1,SHORT\nT02,Topic 02,3,SHORT\n", null);

        assertThat(runner().run(shape("MON 19:00-20:00", "SYN-A:3", null))).isEqualTo(1);
        assertThat(printed.getFirst()).isEqualTo("catalog shape  refused: the files do not validate");
    }

    @Test
    void aSubjectWithNoTopicLeavesTheRatiosUndefinedRatherThanInfinite() {
        write("SYN-A", "code,name,position,effort_tier\n", null);

        assertThat(runner().run(shape("MON 19:00-20:00", "SYN-A:3", null))).isZero();
        assertThat(line("  tightness")).startsWith("  tightness          undefined").endsWith("-> undefined");
        assertThat(line("  density")).startsWith("  density            undefined");
    }

    @Test
    void withoutTheConfiguredBandsTheCommandSaysItCannotRun() {
        assertThat(new CatalogRunner(null, null, root, printed::add)
                .run(shape("MON 19:00-20:00", "SYN-A:3", null))).isEqualTo(2);
        assertThat(printed.getFirst()).contains("needs the configured effort bands");
    }

    private CatalogRunner runner() {
        return new CatalogRunner(null, null, root, printed::add, settings());
    }

    private static CatalogRunner.ShapeSettings settings() {
        return new CatalogRunner.ShapeSettings(BANDS, Period.ofWeeks(4), MONDAY);
    }

    private static CatalogCommand shape(String availability, String goals, String start) {
        return new CatalogCommand(CatalogCommand.Verb.SHAPE, null, false, availability, goals, start);
    }

    private String line(String prefix) {
        return printed.stream().filter(line -> line.startsWith(prefix)).findFirst()
                .orElseThrow(() -> new AssertionError("no line starting with \"" + prefix + "\" in\n"
                        + String.join("\n", printed)));
    }

    private void write(String subject, String topics, String prerequisites) {
        try {
            Path directory = Files.createDirectories(root.resolve(subject));
            Files.writeString(directory.resolve("topics.csv"), topics, StandardCharsets.UTF_8);
            if (prerequisites != null) {
                Files.writeString(directory.resolve("prerequisites.csv"), prerequisites, StandardCharsets.UTF_8);
            }
        } catch (IOException unwritable) {
            throw new UncheckedIOException(unwritable);
        }
    }
}
