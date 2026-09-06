package br.com.sinapse.platform.learningrecord.api;

/**
 * Where a session is in its life.
 *
 * <p>{@link #IN_PROGRESS} is the only state in which a session can be changed. Once closed it
 * is frozen, by trigger: correcting a closed session is a new record, not an edit, because
 * evidence that can be edited is not evidence.
 */
public enum SessionStatus {

    /** Running. At most one per account. */
    IN_PROGRESS,

    /** Finished, with a recall rating. */
    COMPLETED,

    /** Given up on, with no rating to give. */
    ABANDONED;

    /** Whether the session is closed and therefore frozen. */
    public boolean isClosed() {
        return this != IN_PROGRESS;
    }
}
