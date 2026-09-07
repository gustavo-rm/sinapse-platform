package br.com.sinapse.platform.curation.internal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The git revision the catalogue files came from.
 *
 * <p>This is the field that makes {@code catalog_import} worth having. Without it the row records
 * that <em>an</em> import happened; with it, the exact content that was applied can be checked
 * out and read. The ablation experiment needs the second.
 *
 * <p>Shelling out to git is deliberate. This is a command-line tool that runs beside a working
 * copy, git is the thing that knows the answer, and a library would be a dependency added to the
 * whole application for one string read by one profile.
 *
 * <p><strong>A dirty tree is reported, not hidden.</strong> If {@code catalog/} has uncommitted
 * changes then no commit describes what is being applied, and recording the last commit anyway
 * would be recording something false. The suffix makes that visible in the table forever.
 */
public final class GitRevision {

    /** What is recorded when git cannot answer at all. */
    static final String UNKNOWN = "unknown";

    private static final int TIMEOUT_SECONDS = 10;

    private GitRevision() {
    }

    /**
     * Resolves the revision of a directory.
     *
     * @param directory catalogue directory
     * @return the commit, with {@code -dirty} appended when it has uncommitted changes, or
     *         {@link #UNKNOWN} when git cannot be asked
     */
    public static String of(Path directory) {
        String commit = run(directory, List.of("git", "log", "-1", "--format=%H", "--", "."));
        if (commit == null || commit.isBlank()) {
            return UNKNOWN;
        }
        String dirty = run(directory, List.of("git", "status", "--porcelain", "--", "."));
        return dirty == null || dirty.isBlank() ? commit : commit + "-dirty";
    }

    private static String run(Path directory, List<String> command) {
        try {
            Process process = new ProcessBuilder(command)
                    .directory(directory.toFile())
                    .redirectErrorStream(false)
                    .start();
            String output;
            try (var stream = process.getInputStream()) {
                output = new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
            }
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return null;
            }
            return process.exitValue() == 0 ? output : null;
        } catch (IOException unavailable) {
            return null;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return null;
        }
    }
}
