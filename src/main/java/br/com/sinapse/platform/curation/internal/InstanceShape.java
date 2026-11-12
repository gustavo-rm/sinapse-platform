package br.com.sinapse.platform.curation.internal;

import br.com.sinapse.platform.curation.internal.model.DesiredCatalogue;
import br.com.sinapse.platform.curation.internal.model.DesiredEdge;
import br.com.sinapse.platform.curation.internal.model.DesiredTopic;
import br.com.sinapse.platform.curriculum.api.EdgeProvenance;
import br.com.sinapse.platform.curriculum.api.EdgeStrength;
import br.com.sinapse.platform.curriculum.api.EffortTier;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The shape of the instance a catalogue produces for one student, in the core's terms.
 *
 * <p>The bridge between the catalogue and the core's measurement. The core characterised its
 * engines on a matrix of instances ({@code benchmarks/README.md} in the core repository, read at
 * {@code other-repo@9e13262}), and a result on a real catalogue says something only if the
 * catalogue falls where the matrix measured. This answers that before anyone spends a domain
 * expert's time on a subject.
 *
 * <p><strong>The definitions are the core's, not new ones</strong>, and they are applied to the
 * instance the platform would actually send:
 *
 * <ul>
 *   <li>{@code demanded_minutes}: the sum of {@code estimatedMinutes}, first passes only — each
 *       topic in scope at its band's configured minutes, with no per-student adjustment, which is
 *       what the platform sends for a student with no history;</li>
 *   <li>{@code available_minutes}: the minutes of availability inside the horizon — the weekly
 *       windows laid over every day of it, the last day included, as the snapshot assembly lays
 *       them;</li>
 *   <li>{@code tightness}: available ÷ demanded. Below 1 the calendar cannot hold the first
 *       passes; the matrix measured 0.7, 1.0 and 1.5;</li>
 *   <li>{@code density}: prerequisite edges per topic, counting the edges the platform sends
 *       (every edge touching a subject being pursued) over the topics it sends; the matrix
 *       measured 0.4 and 1.2;</li>
 *   <li>{@code topics}: topics in scope, which is every topic of every subject with a goal
 *       (decision L3); the matrix measured 10 and 25.</li>
 * </ul>
 *
 * <p><strong>Importance dispersion is not computed</strong>, because the core's README does not
 * define it: importance is held fixed at {@code goal-priority} there and is not an axis of the
 * matrix. What is printed instead is the raw input that strategy reads — each topic inherits its
 * subject's goal priority — so that the reader can see whether importance varies at all.
 */
final class InstanceShape {

    /** The core's measured levels, from the matrix in its {@code benchmarks/README.md}. */
    static final double[] MATRIX_TOPICS = {10, 25};

    /** Prerequisite edges per topic, as the matrix measured them. */
    static final double[] MATRIX_DENSITY = {0.4, 1.2};

    /** Available over demanded minutes, as the matrix measured them. */
    static final double[] MATRIX_TIGHTNESS = {0.7, 1.0, 1.5};

    private static final Map<String, DayOfWeek> DAYS = Map.of(
            "MON", DayOfWeek.MONDAY, "TUE", DayOfWeek.TUESDAY, "WED", DayOfWeek.WEDNESDAY,
            "THU", DayOfWeek.THURSDAY, "FRI", DayOfWeek.FRIDAY, "SAT", DayOfWeek.SATURDAY,
            "SUN", DayOfWeek.SUNDAY);

    private InstanceShape() {
    }

    /**
     * One weekly availability window, as a student declares it.
     *
     * @param day   weekday
     * @param start local start
     * @param end   local end, after the start: a window does not cross midnight, as on the
     *              platform
     */
    record Window(DayOfWeek day, LocalTime start, LocalTime end) {

        long minutes() {
            return Duration.between(start, end).toMinutes();
        }
    }

    /**
     * A value read from the command line, or why it could not be read.
     *
     * @param value what was read, or {@code null}
     * @param error what is wrong with it, or {@code null}
     * @param <T>   the type read
     */
    record Read<T>(T value, String error) {

        static <T> Read<T> of(T value) {
            return new Read<>(value, null);
        }

        static <T> Read<T> failed(String error) {
            return new Read<>(null, error);
        }
    }

    /**
     * Reads a weekly availability: {@code MON-FRI 19:00-20:00; SAT 09:00-10:30}.
     *
     * <p>Entries are separated by {@code ;}. Each names its days — one ({@code SAT}), a range
     * ({@code MON-FRI}) or a list ({@code MON,WED,FRI}) — and a local time range.
     *
     * @param text the option's value
     * @return the windows, or what is wrong with the text
     */
    static Read<List<Window>> availability(String text) {
        if (text == null || text.isBlank()) {
            return Read.failed("--availability is required, for example"
                    + " --availability=\"MON-FRI 19:00-20:00; SAT 09:00-10:30\"");
        }
        List<Window> windows = new ArrayList<>();
        for (String entry : text.split(";")) {
            String[] parts = entry.trim().split("\\s+");
            if (parts.length != 2) {
                return Read.failed("cannot read availability entry \"%s\": write days, a space and"
                        + " a time range, as in MON-FRI 19:00-20:00".formatted(entry.trim()));
            }
            List<DayOfWeek> days = days(parts[0]);
            String[] times = parts[1].split("-");
            LocalTime start = times.length == 2 ? time(times[0]) : null;
            LocalTime end = times.length == 2 ? time(times[1]) : null;
            if (days == null || start == null || end == null || !end.isAfter(start)) {
                return Read.failed(("cannot read availability entry \"%s\": days are MON … SUN, a"
                        + " range MON-FRI or a list MON,WED; times are HH:MM-HH:MM, ending after"
                        + " they start on the same day").formatted(entry.trim()));
            }
            days.forEach(day -> windows.add(new Window(day, start, end)));
        }
        return Read.of(List.copyOf(windows));
    }

    /**
     * Reads the goals: {@code MED-X:5,MED-Y:3}, subject code and priority from 1 to 5.
     *
     * @param text the option's value
     * @return subject code to priority, in the order written, or what is wrong with the text
     */
    static Read<Map<String, Integer>> goals(String text) {
        if (text == null || text.isBlank()) {
            return Read.failed("--goals is required, for example --goals=SUBJECT-A:5,SUBJECT-B:3");
        }
        Map<String, Integer> goals = new LinkedHashMap<>();
        for (String entry : text.split(",")) {
            String[] parts = entry.trim().split(":");
            Integer priority = parts.length == 2 ? number(parts[1].trim()) : null;
            if (priority == null || priority < 1 || priority > 5 || parts[0].isBlank()) {
                return Read.failed(("cannot read goal \"%s\": write the subject code, a colon and a"
                        + " priority from 1 to 5, as on the platform").formatted(entry.trim()));
            }
            goals.merge(parts[0].trim(), priority, Math::max);
        }
        return Read.of(goals);
    }

    /**
     * Reads the first day of the horizon.
     *
     * @param text the option's value, or {@code null} for the default
     * @param today what the platform would use: today, in the application's zone
     * @return the day, or what is wrong with the text
     */
    static Read<LocalDate> start(String text, LocalDate today) {
        if (text == null) {
            return Read.of(today);
        }
        try {
            return Read.of(LocalDate.parse(text));
        } catch (DateTimeParseException unreadable) {
            return Read.failed("cannot read --start \"%s\": write year-month-day, for example 2026-10-12"
                    .formatted(text));
        }
    }

    /**
     * Computes the shape.
     *
     * @param desired  the catalogue, already validated
     * @param bands    the band-to-minutes mapping in force
     * @param start    first day of the horizon
     * @param horizon  the configured horizon; the last day is {@code start + horizon}, included
     * @param windows  weekly availability
     * @param goals    subject code to goal priority
     * @return the shape, or what is wrong with the request
     */
    static Read<Shape> of(DesiredCatalogue desired, Map<EffortTier, Duration> bands,
            LocalDate start, Period horizon, List<Window> windows, Map<String, Integer> goals) {

        for (String subject : goals.keySet()) {
            if (!desired.subjects().containsKey(subject)) {
                return Read.failed("goal names subject %s, which is not in the catalogue read"
                        .formatted(subject));
            }
        }
        Set<String> scope = goals.keySet();
        List<DesiredTopic> topics = desired.topics().stream()
                .filter(topic -> scope.contains(topic.key().subjectCode()))
                .toList();
        // Every edge with at least one end in a subject being pursued, as the platform sends them
        // (PrerequisiteGraph.edgesTouchingSubjects).
        List<DesiredEdge> edges = desired.edges().stream()
                .filter(edge -> scope.contains(edge.prerequisite().subjectCode())
                        || scope.contains(edge.dependent().subjectCode()))
                .toList();

        Map<EffortTier, Integer> perBand = new EnumMap<>(EffortTier.class);
        long demanded = 0;
        for (DesiredTopic topic : topics) {
            perBand.merge(topic.effortTier(), 1, Integer::sum);
            demanded += bands.get(topic.effortTier()).toMinutes();
        }

        LocalDate end = start.plus(horizon);
        long horizonDays = ChronoUnit.DAYS.between(start, end) + 1;
        long available = 0;
        int intervals = 0;
        java.util.Set<LocalDate> daysWithAvailability = new java.util.HashSet<>();
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            for (Window window : windows) {
                if (window.day() == day.getDayOfWeek()) {
                    available += window.minutes();
                    intervals++;
                    daysWithAvailability.add(day);
                }
            }
        }
        long longestWindow = windows.stream().mapToLong(Window::minutes).max().orElse(0);
        List<DesiredTopic> neverFit = topics.stream()
                .filter(topic -> bands.get(topic.effortTier()).toMinutes() > longestWindow)
                .toList();

        Map<Integer, Integer> priorities = new TreeMap<>(java.util.Comparator.reverseOrder());
        topics.forEach(topic -> priorities.merge(goals.get(topic.key().subjectCode()), 1, Integer::sum));

        Map<EdgeStrength, Integer> strengths = new EnumMap<>(EdgeStrength.class);
        Map<EdgeProvenance, Integer> provenances = new EnumMap<>(EdgeProvenance.class);
        edges.forEach(edge -> {
            strengths.merge(edge.strength(), 1, Integer::sum);
            provenances.merge(edge.provenance(), 1, Integer::sum);
        });
        long outsideScope = edges.stream()
                .filter(edge -> !scope.contains(edge.prerequisite().subjectCode())
                        || !scope.contains(edge.dependent().subjectCode()))
                .count();

        return Read.of(new Shape(start, end, horizonDays, Map.copyOf(goals), topics.size(),
                perBand, demanded, available, intervals, daysWithAvailability.size(), longestWindow,
                neverFit, edges.size(), strengths, provenances, outsideScope, priorities));
    }

    /**
     * The computed shape.
     *
     * @param start              first day of the horizon
     * @param end                last day, included
     * @param horizonDays        calendar days of the horizon
     * @param goals              subject code to priority
     * @param topics             topics in scope
     * @param perBand            topics in scope per effort band
     * @param demandedMinutes    first-pass minutes demanded
     * @param availableMinutes   minutes of availability inside the horizon
     * @param intervals          availability intervals inside the horizon
     * @param daysWithWindows    days of the horizon with any availability
     * @param longestWindow      longest weekly window, in minutes
     * @param neverFit           topics whose band is longer than every window
     * @param edges              edges sent
     * @param strengths          edges sent per strength
     * @param provenances        edges sent per provenance, as the files say
     * @param edgesOutsideScope  edges sent with an end in a subject that is not pursued
     * @param priorities         goal priority to number of topics inheriting it, highest first
     */
    record Shape(LocalDate start, LocalDate end, long horizonDays, Map<String, Integer> goals,
            int topics, Map<EffortTier, Integer> perBand, long demandedMinutes,
            long availableMinutes, int intervals, int daysWithWindows, long longestWindow,
            List<DesiredTopic> neverFit, int edges, Map<EdgeStrength, Integer> strengths,
            Map<EdgeProvenance, Integer> provenances, long edgesOutsideScope,
            Map<Integer, Integer> priorities) {

        /** Available over demanded, or {@code NaN} when nothing is demanded. */
        double tightness() {
            return demandedMinutes == 0 ? Double.NaN : (double) availableMinutes / demandedMinutes;
        }

        /** Edges per topic, or {@code NaN} when there is no topic. */
        double density() {
            return topics == 0 ? Double.NaN : (double) edges / topics;
        }
    }

    /**
     * Where a value falls against the matrix's levels.
     *
     * @param value  the value
     * @param levels the measured levels, ascending
     * @return a phrase for the report
     */
    static String against(double value, double[] levels) {
        if (Double.isNaN(value)) {
            return "undefined";
        }
        double low = levels[0];
        double high = levels[levels.length - 1];
        if (value < low) {
            return "below the measured range";
        }
        if (value > high) {
            return "above the measured range";
        }
        return "inside the measured range";
    }

    /**
     * Formats a level list as the report prints it.
     *
     * @param levels levels
     * @return the levels, comma separated
     */
    static String levels(double[] levels) {
        List<String> printed = new ArrayList<>();
        for (double level : levels) {
            printed.add(level == Math.rint(level) && level >= 2
                    ? Long.toString((long) level)
                    : String.format(Locale.ROOT, "%.1f", level));
        }
        return String.join(", ", printed);
    }

    private static List<DayOfWeek> days(String text) {
        String upper = text.toUpperCase(Locale.ROOT);
        List<DayOfWeek> days = new ArrayList<>();
        if (upper.contains("-")) {
            String[] range = upper.split("-");
            DayOfWeek from = range.length == 2 ? DAYS.get(range[0]) : null;
            DayOfWeek to = range.length == 2 ? DAYS.get(range[1]) : null;
            if (from == null || to == null || to.compareTo(from) < 0) {
                return null;
            }
            for (int day = from.getValue(); day <= to.getValue(); day++) {
                days.add(DayOfWeek.of(day));
            }
            return days;
        }
        for (String name : upper.split(",")) {
            DayOfWeek day = DAYS.get(name);
            if (day == null) {
                return null;
            }
            days.add(day);
        }
        return days;
    }

    private static LocalTime time(String text) {
        try {
            return LocalTime.parse(text.trim());
        } catch (DateTimeParseException unreadable) {
            return null;
        }
    }

    private static Integer number(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }
}
