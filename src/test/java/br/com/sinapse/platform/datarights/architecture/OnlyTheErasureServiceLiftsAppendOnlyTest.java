package br.com.sinapse.platform.datarights.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Exactly one service may lift append-only, and this is what keeps it that way.
 *
 * <p>The triggers on {@code study_session}, {@code planned_session} and {@code consent_record}
 * consult a transaction-local setting. That turns erasure into an explicit exception rather than
 * a permanent hole in the rule — but only while there is one place that sets it. A second one,
 * added in good faith by somebody who needed to delete a row, would quietly reopen the hole, and
 * every other test in the suite would still pass.
 *
 * <p>It reads the sources rather than the bytecode because the setting is a string, and a string
 * constant is exactly the thing a bytecode rule does not see.
 */
class OnlyTheErasureServiceLiftsAppendOnlyTest {

    private static final Path SOURCES = Path.of("src/main/java");

    /** The setting the append-only triggers consult. */
    private static final String FLAG = "sinapse.erasure";

    /** The one class allowed to mention it. */
    private static final String ERASURE_SERVICE =
            "br/com/sinapse/platform/datarights/internal/service/ErasureService.java";

    @Test
    void nothingButTheErasureServiceMentionsTheFlag() {
        List<String> mentions = productionSources()
                .filter(OnlyTheErasureServiceLiftsAppendOnlyTest::mentionsTheFlag)
                .map(path -> SOURCES.relativize(path).toString().replace('\\', '/'))
                .toList();

        assertThat(mentions)
                .as("a second place that sets the flag turns an explicit, transaction-local "
                        + "exception back into a permanent hole in the append-only rule")
                .containsExactly(ERASURE_SERVICE);
    }

    private static Stream<Path> productionSources() {
        try {
            return Files.walk(SOURCES).filter(path -> path.toString().endsWith(".java"));
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private static boolean mentionsTheFlag(Path source) {
        try {
            return Files.readString(source).contains(FLAG);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }
}
