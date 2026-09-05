package br.com.sinapse.platform.identity.api;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Purposes consented to separately, as decided in ADR 0004 and listed in section 5.4 of
 * the architecture document.
 *
 * <p>Separation is the point: a single blanket acceptance would make none of the three
 * refusable, and {@link #ACADEMIC_RESEARCH} has to be refusable without any effect on the
 * service, or the consent is not free and the data cannot be published.
 */
public enum ConsentPurpose {

    /** Essential. Without it there is no service to provide. */
    LEARNING_DATA_PROCESSING(true),

    /**
     * Conditional. Without it the holder cannot join a classroom, because a classroom is
     * exactly the sharing. Consented when an invite is redeemed, never at registration:
     * consenting up front to something the holder may never do is not informed.
     */
    INSTITUTION_SHARING(false),

    /** Optional. Refusal has no effect whatsoever on the service. */
    ACADEMIC_RESEARCH(false);

    private static final Set<ConsentPurpose> ESSENTIAL = Collections.unmodifiableSet(
            EnumSet.of(LEARNING_DATA_PROCESSING));

    private final boolean essential;

    ConsentPurpose(boolean essential) {
        this.essential = essential;
    }

    /**
     * Whether an account cannot be {@link AccountStatus#ACTIVE} without a valid consent
     * for this purpose.
     */
    public boolean isEssential() {
        return essential;
    }

    /** The purposes an active account must have consented to. */
    public static Set<ConsentPurpose> essentialPurposes() {
        return ESSENTIAL;
    }
}
