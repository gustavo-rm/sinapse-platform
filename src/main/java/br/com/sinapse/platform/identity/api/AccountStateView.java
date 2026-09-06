package br.com.sinapse.platform.identity.api;

import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;

/**
 * What another component may know about the state an account is in.
 *
 * <p>Published for the initial screen, which has to decide between showing the product and
 * showing something the holder has to resolve first. Every field here is about the state of
 * the relationship rather than about the person: no name, no address, no date of birth. That
 * is what keeps {@link AccountDirectory} narrow while still being useful.
 *
 * @param accountId                     the holder
 * @param status                        where the account stands
 * @param timeZone                      zone the holder's local times are interpreted in
 * @param pendingConsents               purposes the holder has never decided, and which this
 *                                      platform asks about on its own initiative. See
 *                                      {@link ConsentPurpose#isRequestedOnItsOwn()} — a
 *                                      purpose granted in the course of another act is never
 *                                      listed here, because a screen that asked for it up
 *                                      front would be collecting consent for something the
 *                                      holder may never do
 * @param requiresMajorityReaffirmation whether the holder has reached the consent age and has
 *                                      not yet reaffirmed in their own name. The same
 *                                      condition the daily sweep evaluates, so the screen and
 *                                      the sweep cannot disagree
 */
public record AccountStateView(
        UUID accountId,
        AccountStatus status,
        ZoneId timeZone,
        Set<ConsentPurpose> pendingConsents,
        boolean requiresMajorityReaffirmation) {

    /** Copies the set, so a state read cannot change under whoever is rendering it. */
    public AccountStateView {
        pendingConsents = Set.copyOf(pendingConsents);
    }
}
