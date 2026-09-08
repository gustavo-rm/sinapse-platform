package br.com.sinapse.platform.curation.internal.model;

/**
 * One thing wrong with the files, located precisely enough to go and fix.
 *
 * <p>Reported alongside every other problem of the same run rather than thrown. Stopping at the
 * first error makes curating a four-hundred-row file a sequence of forty runs, and a curator who
 * has to do that will stop using the validator — which is the failure that matters, because the
 * validator is the only thing standing between a spreadsheet and a graph nobody can debug.
 *
 * @param file    file the problem is in, relative to the catalogue root
 * @param line    line it is on, or 0 when it is about the file as a whole
 * @param message what is wrong, in a sentence a curator can act on
 */
public record CatalogProblem(String file, int line, String message) {

    /**
     * A problem about a whole file rather than a line.
     *
     * @param file    file
     * @param message what is wrong
     * @return the problem
     */
    public static CatalogProblem inFile(String file, String message) {
        return new CatalogProblem(file, 0, message);
    }

    /** How the report prints it: file, line, then the sentence. */
    @Override
    public String toString() {
        return line > 0 ? "%s:%d  %s".formatted(file, line, message)
                : "%s  %s".formatted(file, message);
    }
}
