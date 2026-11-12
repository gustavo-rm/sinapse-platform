package br.com.sinapse.platform.curation.internal;

import java.util.List;
import java.util.Locale;

/**
 * What the operator asked for.
 *
 * <p>Parsed by hand rather than with an option library, because the surface is six verbs and
 * a handful of options and a library would be a dependency the running application also carries.
 *
 * @param verb               what to do
 * @param subject            subject code the run is limited to, or {@code null}
 * @param allowTopicRemoval  whether a topic absent from the files may be removed
 * @param availability       for {@code shape}: the weekly availability, as written, or {@code null}
 * @param goals              for {@code shape}: the goals, as written, or {@code null}
 * @param start              for {@code shape}: the first day of the horizon, or {@code null}
 */
public record CatalogCommand(Verb verb, String subject, boolean allowTopicRemoval,
        String availability, String goals, String start) {

    /**
     * A command with no {@code shape} options.
     *
     * @param verb              what to do
     * @param subject           subject code the run is limited to, or {@code null}
     * @param allowTopicRemoval whether a topic absent from the files may be removed
     */
    public CatalogCommand(Verb verb, String subject, boolean allowTopicRemoval) {
        this(verb, subject, allowTopicRemoval, null, null, null);
    }

    /** The four commands of ADR 0014, and the two of ADR 0019. */
    public enum Verb {

        /** Parse and validate, touch nothing. */
        VALIDATE,

        /** Show what applying would change. */
        DIFF,

        /** Apply, in one transaction. */
        APPLY,

        /** Emit textbook-order edges into the file. */
        SEED_ORDER,

        /** Print what a reviewer needs to see, from the files. */
        REPORT,

        /** Print the shape of the instance a student would produce, in the core's terms. */
        SHAPE
    }

    /**
     * Reads the arguments the runner was given.
     *
     * @param arguments non-option arguments, with {@code catalog} already stripped or not
     * @param options   flags, as {@code --name} or {@code --name=value}
     * @return the command, or {@code null} when it cannot be read
     */
    public static CatalogCommand parse(List<String> arguments, List<String> options) {
        List<String> words = arguments.stream()
                .filter(word -> !word.equalsIgnoreCase("catalog"))
                .toList();
        if (words.isEmpty()) {
            return null;
        }
        Verb verb = switch (words.getFirst().toLowerCase(Locale.ROOT)) {
            case "validate" -> Verb.VALIDATE;
            case "diff" -> Verb.DIFF;
            case "apply" -> Verb.APPLY;
            case "seed-order" -> Verb.SEED_ORDER;
            case "report" -> Verb.REPORT;
            case "shape" -> Verb.SHAPE;
            default -> null;
        };
        if (verb == null) {
            return null;
        }
        String subject = valueOf(options, "subject");
        if (verb == Verb.SEED_ORDER && subject == null) {
            return null;
        }
        return new CatalogCommand(verb, subject, options.stream()
                .anyMatch(option -> name(option).equals("allow-topic-removal")),
                valueOf(options, "availability"), valueOf(options, "goals"),
                valueOf(options, "start"));
    }

    /** The usage line, printed when the arguments cannot be read. */
    public static String usage() {
        return """
                usage: (options are written --name=value)
                  catalog validate [--subject=<code>]     parse and validate, touch nothing
                  catalog diff [--subject=<code>]         show what would change
                  catalog apply [--subject=<code>] [--allow-topic-removal]
                  catalog seed-order --subject=<code>     emit TEXTBOOK_ORDER edges into the CSV
                  catalog report [--subject=<code>]       what a reviewer needs to see, from the files
                  catalog shape --availability="MON-FRI 19:00-20:00; SAT 09:00-10:30"
                                --goals=SUBJECT-A:5,SUBJECT-B:3 [--start=2026-10-12]
                                                          the instance's shape, in the core's terms""";
    }

    private static String valueOf(List<String> options, String name) {
        for (String option : options) {
            if (!name(option).equals(name)) {
                continue;
            }
            int equals = option.indexOf('=');
            return equals < 0 ? null : option.substring(equals + 1).trim();
        }
        return null;
    }

    private static String name(String option) {
        String stripped = option.startsWith("--") ? option.substring(2) : option;
        int equals = stripped.indexOf('=');
        return (equals < 0 ? stripped : stripped.substring(0, equals)).toLowerCase(Locale.ROOT);
    }
}
