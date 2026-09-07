package br.com.sinapse.platform.curriculum.api;

import br.com.sinapse.platform.shared.web.problem.ApiErrorType;
import br.com.sinapse.platform.shared.web.problem.ApiException;

/**
 * Raised when a topic cannot be removed because something still points at it.
 *
 * <p><strong>This module does not know what points at it, and must not.</strong> Rule R3 keeps
 * curriculum at the base of the graph: it does not know what a study session or a planned
 * session is, so it cannot ask whether one exists. What it can do is attempt the removal and
 * let the foreign keys answer — which they do, from the two modules downstream of here, without
 * this one learning either of their names.
 *
 * <p>That is also why the answer is a refusal rather than a cascade. ADR 0014 puts it plainly:
 * evidence is not deleted as a side effect of an import. A curator who really means it removes
 * the references first, deliberately, somewhere else.
 */
public class TopicStillReferencedException extends ApiException {

    /**
     * @param cause the integrity violation the database raised
     */
    public TopicStillReferencedException(Throwable cause) {
        super(ApiErrorType.CONFLICT, cause);
    }
}
