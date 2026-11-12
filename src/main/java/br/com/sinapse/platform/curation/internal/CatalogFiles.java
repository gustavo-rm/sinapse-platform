package br.com.sinapse.platform.curation.internal;

import br.com.sinapse.platform.curation.internal.csv.Csv;
import br.com.sinapse.platform.curation.internal.csv.CsvRow;
import br.com.sinapse.platform.curation.internal.model.CatalogProblem;
import br.com.sinapse.platform.curation.internal.model.DesiredCatalogue;
import br.com.sinapse.platform.curation.internal.model.DesiredEdge;
import br.com.sinapse.platform.curation.internal.model.DesiredReview;
import br.com.sinapse.platform.curation.internal.model.DesiredTopic;
import br.com.sinapse.platform.curation.internal.model.TopicKey;
import br.com.sinapse.platform.curriculum.api.EdgeProvenance;
import br.com.sinapse.platform.curriculum.api.EdgeStrength;
import br.com.sinapse.platform.curriculum.api.EffortTier;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Reads {@code catalog/} into the state it describes.
 *
 * <p>Everything that can go wrong at this level — a missing column, a malformed row, an
 * unreadable enum, a position that is not a number — becomes a {@link CatalogProblem} rather
 * than an exception, so that one run reports every one of them. What this stage cannot judge is
 * anything that needs the whole picture: unknown references, duplicates and cycles belong to
 * {@link CatalogValidation}.
 *
 * <p>The columns are exactly the four and five that ADR 0014 names, because the authoring
 * surface is a spreadsheet and a format it cannot produce is a format nobody will maintain. Two
 * optional files sit beside them: {@code subject.csv}, a single cell holding the subject's display
 * name, and {@code reviews.csv}, the record of who reviewed the subject's graph and when
 * (ADR 0019).
 *
 * <p><strong>Every column is declared once, in the lists below</strong>, and the authoring
 * template under {@code docs/curadoria/modelo-de-autoria/} is checked against them. Dropping a
 * column — the effort band, if the core's measurement shows it changes nothing — is a change to
 * one list, one reader branch and one template file, not to the tool.
 */
public final class CatalogFiles {

    /** Columns of {@code topics.csv}. */
    static final List<String> TOPIC_COLUMNS = List.of("code", "name", "position", "effort_tier");

    /** Columns of {@code prerequisites.csv}. */
    static final List<String> EDGE_COLUMNS =
            List.of("prerequisite", "dependent", "strength", "provenance", "source_reference");

    /** Columns of {@code reviews.csv}. */
    static final List<String> REVIEW_COLUMNS = List.of("reviewer", "reviewed_on");

    /** Columns of {@code subject.csv}. */
    static final List<String> SUBJECT_COLUMNS = List.of("name");

    /**
     * Every file a subject directory may hold, with its columns, in the order a curator meets
     * them. The authoring template is checked against this, so the two cannot drift apart.
     */
    public static final Map<String, List<String>> COLUMNS_BY_FILE = columnsByFile();

    private CatalogFiles() {
    }

    private static Map<String, List<String>> columnsByFile() {
        Map<String, List<String>> columns = new LinkedHashMap<>();
        columns.put("subject.csv", SUBJECT_COLUMNS);
        columns.put("topics.csv", TOPIC_COLUMNS);
        columns.put("prerequisites.csv", EDGE_COLUMNS);
        columns.put("reviews.csv", REVIEW_COLUMNS);
        return java.util.Collections.unmodifiableMap(columns);
    }

    /**
     * Reads every subject directory, or only one.
     *
     * @param root         catalogue directory
     * @param onlySubject  subject code to read, or {@code null} for all of them
     * @return the desired state and whatever was wrong with the files themselves
     */
    public static DesiredCatalogue read(Path root, String onlySubject) {
        Map<String, String> subjects = new LinkedHashMap<>();
        List<DesiredTopic> topics = new ArrayList<>();
        List<DesiredEdge> edges = new ArrayList<>();
        List<DesiredReview> reviews = new ArrayList<>();
        List<CatalogProblem> problems = new ArrayList<>();

        if (!Files.isDirectory(root)) {
            problems.add(CatalogProblem.inFile(root.toString(), "the catalogue directory does not exist"));
            return new DesiredCatalogue(subjects, topics, edges, reviews, problems);
        }

        for (Path directory : subjectDirectories(root, onlySubject)) {
            String code = directory.getFileName().toString();
            subjects.put(code, subjectName(directory, code, problems));
            readTopics(directory, code, topics, problems);
            readEdges(directory, code, edges, problems);
            readReviews(directory, code, reviews, problems);
        }
        if (subjects.isEmpty()) {
            problems.add(CatalogProblem.inFile(root.toString(), onlySubject == null
                    ? "no subject directories found"
                    : "no directory for subject " + onlySubject));
        }
        return new DesiredCatalogue(subjects, topics, edges, reviews, problems);
    }

    /**
     * The subject directories, in a stable order so that two runs report in the same sequence.
     *
     * <p>Hidden directories are not subjects. The catalogue root is frequently a git working
     * tree in its own right, and it takes one {@code .git} read as a subject with no
     * {@code topics.csv} to make every run report a problem that is not one.
     */
    private static List<Path> subjectDirectories(Path root, String onlySubject) {
        try (Stream<Path> entries = Files.list(root)) {
            return entries.filter(Files::isDirectory)
                    .filter(path -> !path.getFileName().toString().startsWith("."))
                    .filter(path -> onlySubject == null
                            || path.getFileName().toString().equals(onlySubject))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    /**
     * The subject's display name.
     *
     * <p>From {@code subject.csv} when it is there, and otherwise the code itself. The fallback
     * is not a placeholder to be tidied up later: the two files ADR 0014 names carry no subject
     * name at all, so a catalogue that never writes one is a catalogue the ADR fully describes.
     */
    private static String subjectName(Path directory, String code, List<CatalogProblem> problems) {
        Path file = directory.resolve("subject.csv");
        if (!Files.isRegularFile(file)) {
            return code;
        }
        Csv.Parsed parsed = Csv.read(file);
        String relative = code + "/subject.csv";
        if (!parsed.header().containsKey(SUBJECT_COLUMNS.getFirst())) {
            problems.add(CatalogProblem.inFile(relative, "missing required column: name"));
            return code;
        }
        if (parsed.rows().isEmpty()) {
            problems.add(CatalogProblem.inFile(relative, "no row: expected exactly one, holding the name"));
            return code;
        }
        String name = parsed.rows().getFirst().get("name");
        if (name.isEmpty()) {
            problems.add(new CatalogProblem(relative, parsed.rows().getFirst().line(), "name is blank"));
            return code;
        }
        return name;
    }

    private static void readTopics(Path directory, String subject, List<DesiredTopic> topics,
            List<CatalogProblem> problems) {

        String relative = subject + "/topics.csv";
        Path file = directory.resolve("topics.csv");
        if (!Files.isRegularFile(file)) {
            problems.add(CatalogProblem.inFile(relative, "the file is missing"));
            return;
        }
        Csv.Parsed parsed = Csv.read(file);
        if (reportStructure(parsed, relative, TOPIC_COLUMNS, problems)) {
            return;
        }
        for (CsvRow row : parsed.rows()) {
            String code = row.get("code");
            String name = row.get("name");
            if (code.isEmpty()) {
                problems.add(new CatalogProblem(relative, row.line(), "code is blank"));
                continue;
            }
            if (name.isEmpty()) {
                problems.add(new CatalogProblem(relative, row.line(), "name is blank"));
                continue;
            }
            Integer position = positive(row.get("position"));
            if (position == null) {
                problems.add(new CatalogProblem(relative, row.line(),
                        "position is not a whole number: " + quoted(row.get("position"))
                                + ". Number the topics 1, 2, 3 … in the order the subject is taught"));
                continue;
            }
            EffortTier tier = readEnum(EffortTier.class, row.get("effort_tier"));
            if (tier == null) {
                problems.add(new CatalogProblem(relative, row.line(),
                        "effort_tier is " + quoted(row.get("effort_tier"))
                                + ": write one of SHORT, STANDARD, LONG, EXTENDED"));
                continue;
            }
            topics.add(new DesiredTopic(new TopicKey(subject, code), name, position, tier, row.line()));
        }
    }

    private static void readEdges(Path directory, String subject, List<DesiredEdge> edges,
            List<CatalogProblem> problems) {

        String relative = subject + "/prerequisites.csv";
        Path file = directory.resolve("prerequisites.csv");
        if (!Files.isRegularFile(file)) {
            // Absent is legitimate: a subject whose graph has not been curated yet has topics
            // and no edges, and refusing that would make starting a subject impossible.
            return;
        }
        Csv.Parsed parsed = Csv.read(file);
        if (reportStructure(parsed, relative, EDGE_COLUMNS, problems)) {
            return;
        }
        for (CsvRow row : parsed.rows()) {
            TopicKey prerequisite = TopicKey.parse(row.get("prerequisite"));
            TopicKey dependent = TopicKey.parse(row.get("dependent"));
            if (prerequisite == null || dependent == null) {
                problems.add(new CatalogProblem(relative, row.line(),
                        "prerequisite and dependent must both read subject_code:topic_code,"
                                + " for example " + subject + ":<code from topics.csv>"));
                continue;
            }
            EdgeStrength strength = readEnum(EdgeStrength.class, row.get("strength"));
            if (strength == null) {
                problems.add(new CatalogProblem(relative, row.line(),
                        "strength is " + quoted(row.get("strength")) + ": write HARD or SOFT"));
                continue;
            }
            EdgeProvenance provenance = readEnum(EdgeProvenance.class, row.get("provenance"));
            if (provenance == null) {
                problems.add(new CatalogProblem(relative, row.line(),
                        "provenance is " + quoted(row.get("provenance"))
                                + ": write PROPOSED, CURATED, TEXTBOOK_ORDER or DERIVED"));
                continue;
            }
            edges.add(new DesiredEdge(prerequisite, dependent, strength, provenance,
                    row.get("source_reference"), subject, row.line()));
        }
    }

    /**
     * Reads the subject's review record.
     *
     * <p>Absent is legitimate and is the normal state of a subject nobody has reviewed yet: its
     * edges are {@code PROPOSED}. What the absence forbids is {@code CURATED}, and that is
     * {@link CatalogValidation}'s to say, because it needs the edges and the reviews together.
     */
    private static void readReviews(Path directory, String subject, List<DesiredReview> reviews,
            List<CatalogProblem> problems) {

        String relative = subject + "/reviews.csv";
        Path file = directory.resolve("reviews.csv");
        if (!Files.isRegularFile(file)) {
            return;
        }
        Csv.Parsed parsed = Csv.read(file);
        if (reportStructure(parsed, relative, REVIEW_COLUMNS, problems)) {
            return;
        }
        for (CsvRow row : parsed.rows()) {
            String reviewer = row.get("reviewer");
            if (reviewer.isEmpty()) {
                problems.add(new CatalogProblem(relative, row.line(),
                        "reviewer is blank: write who reviewed the graph, as the project"
                                + " identifies its reviewers"));
                continue;
            }
            LocalDate reviewedOn = date(row.get("reviewed_on"));
            if (reviewedOn == null) {
                problems.add(new CatalogProblem(relative, row.line(),
                        "reviewed_on is " + quoted(row.get("reviewed_on"))
                                + ": write the date of the review as year-month-day, for example 2026-11-30"));
                continue;
            }
            reviews.add(new DesiredReview(subject, reviewer, reviewedOn, row.line()));
        }
    }

    /**
     * Reports a missing column or a malformed row.
     *
     * @return whether the file is unusable, in which case its rows are not read at all
     */
    private static boolean reportStructure(Csv.Parsed parsed, String relative,
            List<String> required, List<CatalogProblem> problems) {

        List<String> missing = required.stream()
                .filter(column -> !parsed.header().containsKey(column))
                .toList();
        parsed.malformed().forEach(line -> problems.add(new CatalogProblem(relative, line,
                "malformed row: wrong number of cells, or a quote that never closes")));
        if (!missing.isEmpty()) {
            problems.add(CatalogProblem.inFile(relative,
                    "missing required column(s): " + String.join(", ", missing)));
            return true;
        }
        return false;
    }

    private static <E extends Enum<E>> E readEnum(Class<E> type, String value) {
        if (value.isEmpty()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    private static LocalDate date(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException unreadable) {
            return null;
        }
    }

    private static Integer positive(String value) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed >= 0 ? parsed : null;
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    private static String quoted(String value) {
        return "\"" + value + "\"";
    }
}
