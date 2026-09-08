package br.com.sinapse.platform.curation;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.sinapse.platform.curation.internal.csv.Csv;
import br.com.sinapse.platform.curation.internal.csv.CsvRow;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The narrow slice of CSV a spreadsheet actually exports.
 *
 * <p>Written by hand rather than taken from a library, so it is worth pinning what it does and,
 * more importantly, what it refuses: a quote that never closes is a malformed row and not a cell
 * that swallowed the rest of the file.
 */
class CsvTest {

    @TempDir
    Path directory;

    @Test
    void headersAreCaseInsensitiveBecauseASpreadsheetCapitalisesThem() {
        CsvRow row = rows("Code,NAME,Position\na,A,1\n").getFirst();

        assertThat(row.get("code")).isEqualTo("a");
        assertThat(row.get("name")).isEqualTo("A");
        assertThat(row.get("position")).isEqualTo("1");
    }

    @Test
    void aColumnThatIsNotThereReadsAsEmptyRatherThanFailing() {
        CsvRow row = rows("code,name\na,A\n").getFirst();

        assertThat(row.get("absent")).isEmpty();
    }

    @Test
    void quotesProtectCommasAndADoubledQuoteIsOneQuote() {
        CsvRow row = rows("code,name\na,\"Osteologia, dita \"\"geral\"\"\"\n").getFirst();

        assertThat(row.get("name")).isEqualTo("Osteologia, dita \"geral\"");
    }

    @Test
    void blankLinesAreSkippedAndLineNumbersStillPointAtTheRealLine() {
        List<CsvRow> rows = rows("code,name\na,A\n\nb,B\n");

        assertThat(rows).extracting(CsvRow::line).containsExactly(2, 4);
    }

    @Test
    void anUnclosedQuoteIsAMalformedRowAndNotACellThatAteTheRest() {
        Csv.Parsed parsed = parse("code,name\na,\"A que nunca fecha\nb,B\n");

        assertThat(parsed.malformed()).containsExactly(2);
        assertThat(parsed.rows()).extracting(row -> row.get("code")).containsExactly("b");
    }

    @Test
    void anEmptyFileIsNotAFailure() {
        Csv.Parsed parsed = parse("");

        assertThat(parsed.rows()).isEmpty();
        assertThat(parsed.header()).isEmpty();
    }

    /**
     * Only what needs quoting is quoted.
     *
     * <p>These files are reviewed as a git diff. A writer that quoted every cell would make the
     * next diff of a one-line change look like the whole file moved, which is how a review stops
     * being a review.
     */
    @Test
    void writingQuotesOnlyWhatWouldOtherwiseBreak() {
        Path file = directory.resolve("out.csv");

        Csv.write(file, List.of("a", "b"), List.of(
                List.of("plain", "also plain"),
                List.of("has, comma", "has \"quote\""),
                java.util.Arrays.asList("", "")));

        // Built by concatenation rather than as a text block: the expected content ends in
        // three quote characters, which would close the block early.
        assertThat(read(file)).isEqualTo("a,b\n"
                + "plain,also plain\n"
                + "\"has, comma\",\"has \"\"quote\"\"\"\n"
                + ",\n");
    }

    @Test
    void whatIsWrittenCanBeReadBackUnchanged() {
        Path file = directory.resolve("round-trip.csv");
        Csv.write(file, List.of("code", "name"), List.of(List.of("a", "Osteologia, geral")));

        Csv.Parsed parsed = Csv.read(file);

        assertThat(parsed.malformed()).isEmpty();
        assertThat(parsed.rows()).singleElement()
                .satisfies(row -> assertThat(row.get("name")).isEqualTo("Osteologia, geral"));
    }

    @Test
    void aRowKeepsNeitherItsCellsNorItsHeaderOpenToChange() {
        CsvRow row = new CsvRow(2, new java.util.ArrayList<>(List.of("a")),
                new java.util.LinkedHashMap<>(Map.of("code", 0)));

        assertThat(row.values()).isUnmodifiable();
        assertThat(row.header()).isUnmodifiable();
    }

    private List<CsvRow> rows(String content) {
        return parse(content).rows();
    }

    private Csv.Parsed parse(String content) {
        Path file = directory.resolve("in.csv");
        try {
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException unwritable) {
            throw new UncheckedIOException(unwritable);
        }
        return Csv.read(file);
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }
}
