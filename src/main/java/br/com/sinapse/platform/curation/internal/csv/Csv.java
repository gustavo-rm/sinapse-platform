package br.com.sinapse.platform.curation.internal.csv;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reading and writing the CSV that a spreadsheet exports.
 *
 * <p>Deliberately small. ADR 0014 chose CSV precisely so that a domain expert can export from
 * the tool they already use without a conversion step, and what comes out of a spreadsheet is
 * quoted fields, doubled quotes inside them, and commas inside names — nothing more exotic. A
 * library would bring a dependency and a configuration surface for a format this narrow.
 *
 * <p>What it does <em>not</em> do is accept a newline inside a quoted field. It could, but then
 * a row would no longer correspond to a line, and every error message would have to explain
 * which line it meant. An unterminated quote is reported as a malformed row at the line where it
 * opened, which is where a curator has to go and look anyway.
 */
public final class Csv {

    private Csv() {
    }

    /**
     * Reads a file into rows, keyed by its header.
     *
     * @param file file to read
     * @return the header order and the data rows
     * @throws UncheckedIOException if the file cannot be read
     */
    public static Parsed read(Path file) {
        List<String> lines = readLines(file);
        if (lines.isEmpty()) {
            return new Parsed(Map.of(), List.of(), List.of(1));
        }
        Map<String, Integer> header = headerOf(split(lines.getFirst()));

        List<CsvRow> rows = new ArrayList<>();
        List<Integer> malformed = new ArrayList<>();
        for (int index = 1; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line.isBlank()) {
                continue;
            }
            List<String> values = split(line);
            if (values == null || values.size() != header.size()) {
                malformed.add(index + 1);
                continue;
            }
            rows.add(new CsvRow(index + 1, values, header));
        }
        return new Parsed(header, rows, malformed);
    }

    /**
     * Writes a header and rows back out, quoting only what needs it.
     *
     * <p>Minimal quoting on purpose: the file is reviewed as a git diff, and a writer that
     * quoted every cell would make every future diff look like the whole file changed.
     *
     * @param file    file to write
     * @param header  column names, in order
     * @param rows    values per row, in the same order
     * @throws UncheckedIOException if the file cannot be written
     */
    public static void write(Path file, List<String> header, List<List<String>> rows) {
        StringBuilder out = new StringBuilder();
        out.append(String.join(",", header.stream().map(Csv::quote).toList())).append('\n');
        for (List<String> row : rows) {
            out.append(String.join(",", row.stream().map(Csv::quote).toList())).append('\n');
        }
        try {
            Files.writeString(file, out.toString(), StandardCharsets.UTF_8);
        } catch (IOException unwritable) {
            throw new UncheckedIOException(unwritable);
        }
    }

    private static List<String> readLines(Path file) {
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private static Map<String, Integer> headerOf(List<String> cells) {
        Map<String, Integer> header = new LinkedHashMap<>();
        if (cells == null) {
            return header;
        }
        for (int index = 0; index < cells.size(); index++) {
            header.put(cells.get(index).trim().toLowerCase(java.util.Locale.ROOT), index);
        }
        return header;
    }

    /**
     * Splits one line, or answers {@code null} when the quoting does not close.
     */
    private static List<String> split(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (quoted) {
                if (character != '"') {
                    cell.append(character);
                } else if (index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    cell.append('"');
                    index++;
                } else {
                    quoted = false;
                }
            } else if (character == '"' && cell.isEmpty()) {
                quoted = true;
            } else if (character == ',') {
                values.add(cell.toString());
                cell.setLength(0);
            } else {
                cell.append(character);
            }
        }
        if (quoted) {
            return null;
        }
        values.add(cell.toString());
        return values;
    }

    private static String quote(String value) {
        String safe = value == null ? "" : value;
        if (safe.indexOf(',') < 0 && safe.indexOf('"') < 0) {
            return safe;
        }
        return '"' + safe.replace("\"", "\"\"") + '"';
    }

    /**
     * What one file parsed to.
     *
     * @param header    column name to index
     * @param rows      the well-formed data rows
     * @param malformed lines whose cell count or quoting was wrong
     */
    public record Parsed(Map<String, Integer> header, List<CsvRow> rows, List<Integer> malformed) {

        /** Copies everything, so a parse result cannot change under its reader. */
        public Parsed {
            header = Map.copyOf(header);
            rows = List.copyOf(rows);
            malformed = List.copyOf(malformed);
        }
    }
}
