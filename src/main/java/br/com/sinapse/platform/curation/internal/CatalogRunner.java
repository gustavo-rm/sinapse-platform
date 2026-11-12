package br.com.sinapse.platform.curation.internal;

import br.com.sinapse.platform.curation.internal.model.CatalogProblem;
import br.com.sinapse.platform.curation.internal.model.DesiredCatalogue;
import br.com.sinapse.platform.curriculum.api.EffortTier;
import br.com.sinapse.platform.curriculum.api.EffortTiers;
import br.com.sinapse.platform.curriculum.api.TopicStillReferencedException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * The {@code catalog} command.
 *
 * <p>An {@link ApplicationRunner} behind its own profile, and not an HTTP endpoint. ADR 0014
 * gives two reasons and both stand: a curation screen would cost more than it is worth for a
 * pilot, and it would need to know who is allowed to curate — which is decision P2, still open.
 * A command line run by whoever has the database credentials sidesteps the open decision
 * without pre-empting it.
 *
 * <pre>
 *   java -jar platform.jar --spring.profiles.active=catalog catalog validate
 *   java -jar platform.jar --spring.profiles.active=catalog catalog diff
 *   java -jar platform.jar --spring.profiles.active=catalog catalog apply
 *   java -jar platform.jar --spring.profiles.active=catalog catalog seed-order --subject=MED-ANAT
 *   java -jar platform.jar --spring.profiles.active=catalog catalog report
 *   java -jar platform.jar --spring.profiles.active=catalog catalog shape \
 *       --availability="MON-FRI 19:00-20:00; SAT 09:00-10:30" --goals=MED-ANAT:2
 * </pre>
 *
 * <p>Output goes to standard output rather than to the logger. A curator running a command
 * wants the answer, not a structured log line with a correlation id; and the logging
 * configuration of this application is built to keep student data out of logs, which is a
 * different job from talking to the person at the terminal.
 */
@Component
@Profile("catalog")
public class CatalogRunner implements ApplicationRunner, ExitCodeGenerator {

    /** Returned when the files are wrong, so that a pipeline running this can fail. */
    static final int PROBLEMS_FOUND = 1;

    /** Returned when the command itself could not be read. */
    static final int BAD_USAGE = 2;

    private final CatalogDiffer differ;
    private final CatalogApplier applier;
    private final Path root;
    private final Consumer<String> out;
    private final ShapeSettings shapeSettings;

    private int exitCode;

    /**
     * @param differ      what a run would change
     * @param applier     the one thing here that writes
     * @param properties  where the catalogue lives, and the horizon {@code shape} uses
     * @param effortTiers the band-to-minutes mapping in force, for {@code shape}
     * @param clock       application clock, for the default first day of {@code shape}
     */
    @Autowired
    public CatalogRunner(CatalogDiffer differ, CatalogApplier applier, CatalogProperties properties,
            EffortTiers effortTiers, Clock clock) {
        this(differ, applier, Path.of(properties.directory()), System.out::println,
                new ShapeSettings(effortTiers.mapping(), properties.horizon(), clock));
    }

    /**
     * The same runner against a chosen directory and a chosen sink.
     *
     * <p>Public so that a test can watch what an operator would see. It widens nothing: this
     * class is in an {@code internal} package, so no other module can reach it whatever its
     * members say, and the build enforces that rather than trusting it.
     *
     * @param differ  what a run would change
     * @param applier the one thing here that writes
     * @param root    catalogue directory
     * @param out     where output goes
     */
    public CatalogRunner(CatalogDiffer differ, CatalogApplier applier, Path root, Consumer<String> out) {
        this(differ, applier, root, out, null);
    }

    /**
     * The same runner, able to compute {@code shape}.
     *
     * @param differ        what a run would change
     * @param applier       the one thing here that writes
     * @param root          catalogue directory
     * @param out           where output goes
     * @param shapeSettings what {@code shape} needs from configuration, or {@code null}
     */
    public CatalogRunner(CatalogDiffer differ, CatalogApplier applier, Path root, Consumer<String> out,
            ShapeSettings shapeSettings) {
        this.differ = differ;
        this.applier = applier;
        this.root = root;
        this.out = out;
        this.shapeSettings = shapeSettings;
    }

    /**
     * What {@code shape} reads from configuration rather than from the command line.
     *
     * @param bands   band-to-minutes mapping in force
     * @param horizon planning horizon
     * @param clock   application clock: today in its zone is the default first day, as for a job
     */
    public record ShapeSettings(Map<EffortTier, Duration> bands, Period horizon, Clock clock) {
    }

    @Override
    public void run(ApplicationArguments arguments) {
        run(CatalogCommand.parse(arguments.getNonOptionArgs(),
                arguments.getOptionNames().stream()
                        .map(name -> arguments.getOptionValues(name).isEmpty()
                                ? "--" + name
                                : "--" + name + "=" + arguments.getOptionValues(name).getFirst())
                        .toList()));
    }

    /**
     * Runs one command.
     *
     * @param command what to do, or {@code null} when the arguments could not be read
     * @return the exit code
     */
    public int run(CatalogCommand command) {
        if (command == null) {
            out.accept(CatalogCommand.usage());
            return exitCode = BAD_USAGE;
        }
        return exitCode = switch (command.verb()) {
            case VALIDATE -> validate(command);
            case DIFF -> diff(command);
            case APPLY -> apply(command);
            case SEED_ORDER -> seedOrder(command);
            case REPORT -> report(command);
            case SHAPE -> shape(command);
        };
    }

    private int validate(CatalogCommand command) {
        DesiredCatalogue desired = CatalogFiles.read(root, command.subject());
        List<CatalogProblem> problems = CatalogValidation.validate(desired);
        out.accept("catalog validate  (%d subject(s), %d topic(s), %d edge(s))".formatted(
                desired.subjects().size(), desired.topics().size(), desired.edges().size()));
        CatalogReport.problems(problems, out);
        CatalogReport.warnings(CatalogValidation.warnings(desired), out);
        return problems.isEmpty() ? 0 : PROBLEMS_FOUND;
    }

    private int diff(CatalogCommand command) {
        DesiredCatalogue desired = CatalogFiles.read(root, command.subject());
        List<CatalogProblem> problems = validatedAgainstTheCatalogue(desired);
        if (!problems.isEmpty()) {
            out.accept("catalog diff  refused: the files do not validate");
            CatalogReport.problems(problems, out);
            return PROBLEMS_FOUND;
        }
        out.accept("catalog diff");
        CatalogReport.diff(differ.diff(desired), out);
        CatalogReport.warnings(CatalogValidation.warnings(desired), out);
        return 0;
    }

    /**
     * The files' own problems, and then the cycles they would close with what is already in the
     * catalogue.
     *
     * <p>The second check needs the database and only means something once the files are
     * sound, so it runs only when the first finds nothing.
     */
    private List<CatalogProblem> validatedAgainstTheCatalogue(DesiredCatalogue desired) {
        List<CatalogProblem> problems = CatalogValidation.validate(desired);
        return problems.isEmpty() ? differ.cyclesThroughTheCatalogue(desired) : problems;
    }

    /** The review report: files only, nothing read from or written to the catalogue. */
    private int report(CatalogCommand command) {
        DesiredCatalogue desired = CatalogFiles.read(root, command.subject());
        List<CatalogProblem> problems = CatalogValidation.validate(desired);
        if (!problems.isEmpty()) {
            out.accept("catalog report  refused: the files do not validate");
            CatalogReport.problems(problems, out);
            return PROBLEMS_FOUND;
        }
        out.accept("catalog report  (%d subject(s), from the files)".formatted(desired.subjects().size()));
        ReviewReport.print(desired, out);
        CatalogReport.warnings(CatalogValidation.warnings(desired), out);
        return 0;
    }

    /**
     * The shape of the instance one student would produce: the bridge to the core's matrix.
     *
     * <p>Reads every subject, whatever {@code --subject} says: the goals decide the scope, and an
     * edge into a subject that was not read would make the count wrong.
     */
    private int shape(CatalogCommand command) {
        if (shapeSettings == null) {
            out.accept("catalog shape  is not available here: it needs the configured effort bands");
            return BAD_USAGE;
        }
        InstanceShape.Read<List<InstanceShape.Window>> windows =
                InstanceShape.availability(command.availability());
        InstanceShape.Read<Map<String, Integer>> goals = InstanceShape.goals(command.goals());
        InstanceShape.Read<LocalDate> start = InstanceShape.start(command.start(),
                LocalDate.now(shapeSettings.clock()));
        for (String error : Stream.of(windows.error(), goals.error(), start.error())
                .filter(Objects::nonNull).toList()) {
            out.accept("catalog shape  " + error);
        }
        if (windows.error() != null || goals.error() != null || start.error() != null) {
            return BAD_USAGE;
        }

        DesiredCatalogue desired = CatalogFiles.read(root, null);
        List<CatalogProblem> problems = CatalogValidation.validate(desired);
        if (!problems.isEmpty()) {
            out.accept("catalog shape  refused: the files do not validate");
            CatalogReport.problems(problems, out);
            return PROBLEMS_FOUND;
        }
        InstanceShape.Read<InstanceShape.Shape> shape = InstanceShape.of(desired, shapeSettings.bands(),
                start.value(), shapeSettings.horizon(), windows.value(), goals.value());
        if (shape.error() != null) {
            out.accept("catalog shape  " + shape.error());
            return BAD_USAGE;
        }
        ShapeReport.print(shape.value(), shapeSettings, out);
        return 0;
    }

    /**
     * Applies, but only after validating.
     *
     * <p>Validation is not optional here even though {@code validate} exists as its own command.
     * A curator who forgot to run it would otherwise meet the database trigger instead, which
     * raises on the first offending edge and says nothing about the path.
     */
    private int apply(CatalogCommand command) {
        DesiredCatalogue desired = CatalogFiles.read(root, command.subject());
        List<CatalogProblem> problems = validatedAgainstTheCatalogue(desired);
        if (!problems.isEmpty()) {
            out.accept("catalog apply  refused: the files do not validate");
            CatalogReport.problems(problems, out);
            return PROBLEMS_FOUND;
        }

        String revision = GitRevision.of(root);
        if (revision.equals(GitRevision.UNKNOWN)) {
            out.accept("  WARNING: git could not name a revision for these files. The import will");
            out.accept("  be recorded as \"unknown\", and no result will be attributable to it.");
        } else if (revision.endsWith("-dirty")) {
            out.accept("  WARNING: the catalogue has uncommitted changes. No commit describes what");
            out.accept("  is being applied, so the revision is recorded with a -dirty suffix.");
        }

        try {
            CatalogApplier.Applied applied = applier.apply(desired, revision, command.allowTopicRemoval());
            out.accept("catalog apply  (revision %s)".formatted(revision));
            CatalogReport.diff(applied.diff(), out);
            applied.removed().forEach(topic ->
                    out.accept("    - topic  %s  %s  (removed)".formatted(topic.code(), topic.name())));
            out.accept("  recorded as import %s".formatted(applied.importId()));
            return 0;
        } catch (TopicStillReferencedException referenced) {
            out.accept("catalog apply  rolled back: a topic absent from the files is still");
            out.accept("  referenced by study history. Nothing was changed. Evidence is not deleted");
            out.accept("  as a side effect of an import: remove the references first, deliberately.");
            return PROBLEMS_FOUND;
        }
    }

    private int seedOrder(CatalogCommand command) {
        DesiredCatalogue desired = CatalogFiles.read(root, command.subject());
        List<CatalogProblem> problems = CatalogValidation.validate(desired);
        if (!problems.isEmpty()) {
            out.accept("catalog seed-order  refused: the files do not validate");
            CatalogReport.problems(problems, out);
            return PROBLEMS_FOUND;
        }
        TextbookOrderSeeder.Result result = TextbookOrderSeeder.seed(root, command.subject(), desired);
        out.accept("catalog seed-order  %s".formatted(command.subject()));
        out.accept("  %d topic(s) in curricular order".formatted(result.topics()));
        out.accept("  %d edge(s) written, %d consecutive pair(s) already had one and were untouched"
                .formatted(result.created(), result.kept()));
        out.accept("  the edges were written to the file. Run \"catalog apply\" to put them in the"
                + " catalogue.");
        return 0;
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}
