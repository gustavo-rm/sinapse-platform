package br.com.sinapse.platform.datarights.api;

/**
 * Where an erasure request stands.
 *
 * <p>The window between {@code REQUESTED} and everything else is the point of the design. ADR
 * 0011 gives the holder seven days to change their mind, because an erasure is irreversible and
 * a request can be accidental or made under pressure. The account is suspended for the whole of
 * it, so the delay costs the holder nothing they were still using.
 */
public enum ErasureRequestStatus {

    /** Made, not yet effective. The holder may still withdraw it. */
    REQUESTED,

    /** Carried out. Nothing about the erased data is kept, including on this row. */
    COMPLETED,

    /** Withdrawn by the holder within the window. The account goes back to active. */
    CANCELLED,

    /**
     * Attempted and rolled back whole.
     *
     * <p>Partial erasure is worse than none, so a failure leaves the account exactly as it was
     * and the request marked for a person to look at. It is not retried on a schedule: whatever
     * broke will break again, and an erasure looping against a broken module is worse than one
     * that stopped and said so.
     */
    FAILED;

    /** Whether the request is still waiting for its window to elapse. */
    public boolean isOpen() {
        return this == REQUESTED;
    }
}
