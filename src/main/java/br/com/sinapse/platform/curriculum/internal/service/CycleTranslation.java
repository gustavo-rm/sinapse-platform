package br.com.sinapse.platform.curriculum.internal.service;

import br.com.sinapse.platform.curriculum.api.PrerequisiteCycleException;
import br.com.sinapse.platform.curriculum.internal.error.DuplicateEdgeException;
import java.sql.SQLException;

/**
 * Turns the database's refusals into failures a caller can act on.
 *
 * <p>Two things reject a write to the graph, and both arrive as a driver exception whose
 * message names identifiers:
 *
 * <ul>
 *   <li>the acyclicity trigger, raised from PL/pgSQL as {@code P0001};</li>
 *   <li>{@code uq_prerequisite_edge}, the same pair asserted twice.</li>
 * </ul>
 *
 * <p>An edge from a topic to itself is not a third case, even though the schema has a check
 * constraint for it. PostgreSQL runs {@code BEFORE} row triggers before evaluating check
 * constraints, so the trigger sees a self-edge first and reports it as the degenerate cycle
 * it is. That is also the right answer for a curator: a topic cannot come before itself,
 * whether directly or by going around.
 *
 * <p>What matters here is that the raw message stops. It names two UUIDs, and ADR 0009 keeps
 * identifiers out of response bodies. It stays in the log, attached as the cause.
 *
 * <p>The trigger reports the <em>first</em> offending edge and nothing about the shape of the
 * cycle, which tells a curator where the problem was noticed and not where to cut. That is
 * exactly why the catalogue importer of ADR 0014 detects cycles in memory and prints the full
 * path: this translation is the last line of defence, not the diagnostic.
 */
final class CycleTranslation {

    /** SQLSTATE of a bare {@code raise exception} in PL/pgSQL. */
    private static final String RAISE_EXCEPTION = "P0001";

    /** Fragment the acyclicity trigger puts in its message. */
    private static final String CYCLE_MARKER = "would introduce a cycle";

    /** Constraint that refuses a second edge on the same ordered pair. */
    private static final String DUPLICATE_EDGE_CONSTRAINT = "uq_prerequisite_edge";

    /** How far down a cause chain to look before giving up. */
    private static final int MAX_CAUSE_DEPTH = 8;

    private CycleTranslation() {
    }

    /**
     * Rethrows a write failure as a domain failure, or as itself when it is neither of the
     * refusals this module knows about.
     *
     * @param failure exception raised by the write
     * @return never returns; declared so the caller can write {@code throw rethrow(e)} and
     *         keep the compiler informed that the path ends
     */
    static RuntimeException rethrow(RuntimeException failure) {
        String state = sqlStateOf(failure);
        String text = messageOf(failure);

        if (RAISE_EXCEPTION.equals(state) && text.contains(CYCLE_MARKER)) {
            throw new PrerequisiteCycleException(failure);
        }
        if (text.contains(DUPLICATE_EDGE_CONSTRAINT)) {
            throw new DuplicateEdgeException(failure);
        }
        throw failure;
    }

    private static String sqlStateOf(Throwable failure) {
        Throwable current = failure;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (current instanceof SQLException sqlException && sqlException.getSQLState() != null) {
                return sqlException.getSQLState();
            }
            current = current.getCause() == current ? null : current.getCause();
        }
        return "";
    }

    /**
     * The whole cause chain as one string.
     *
     * <p>Matching on message text is not elegant, and the alternative is worse: the trigger
     * reports a condition rather than a named constraint, so there is no code to match on.
     * The marker it is matched against is a literal of {@code V2__curriculum.sql}, and a
     * migration that changed it would fail the tests that assert this translation.
     */
    private static String messageOf(Throwable failure) {
        StringBuilder text = new StringBuilder();
        Throwable current = failure;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (current.getMessage() != null) {
                text.append(current.getMessage()).append(' ');
            }
            current = current.getCause() == current ? null : current.getCause();
        }
        return text.toString();
    }
}
