package br.com.sinapse.platform.curation.internal.csv;

import java.util.List;
import java.util.Map;

/**
 * One data row, with the line it came from.
 *
 * <p>The line number travels with the row all the way to the error report, because "column
 * effort_tier is invalid" without it is useless on a file of four hundred rows — and curation
 * files get that long.
 *
 * @param line   1-based line in the file, counting the header
 * @param values the cells, in file order
 * @param header column name to index, shared with every row of the file
 */
public record CsvRow(int line, List<String> values, Map<String, Integer> header) {

    /** Copies the cells, so a row cannot change after it was read. */
    public CsvRow {
        values = List.copyOf(values);
        header = Map.copyOf(header);
    }

    /**
     * The value of a named column, trimmed, or an empty string when absent or blank.
     *
     * @param column column name
     * @return its value, never {@code null}
     */
    public String get(String column) {
        Integer index = header.get(column);
        if (index == null || index >= values.size()) {
            return "";
        }
        return values.get(index).trim();
    }
}
