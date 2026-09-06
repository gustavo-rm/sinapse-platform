package br.com.sinapse.platform.identity.api;

import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

/**
 * The little that another context legitimately needs to know about an account.
 *
 * <p>Two things. The zone the holder's local times are read in — availability is declared as
 * "Tuesday, seven to nine in the evening", and turning that into an instant on a calendar
 * requires the zone — and the state the account is in, which is what the initial screen needs
 * in order to decide between showing the product and showing something the holder has to
 * resolve first. The module that holds the account is the one that should answer both, rather
 * than every other context keeping a copy that drifts.
 *
 * <p>Deliberately narrow. This is not a place to publish an account: the name, the address and
 * the date of birth are personal data with no caller outside this module, and a directory that
 * returned them would become the way they leak. Nothing here identifies a person — a zone, a
 * status, a set of purposes — and that is the line, not the number of methods.
 *
 * <p>Nothing here checks authorisation. Whether the caller may act on this account is
 * {@link AccountAccessPolicy}, asked before this is reached.
 */
public interface AccountDirectory {

    /**
     * The zone an account's local times are interpreted in.
     *
     * @param accountId account
     * @return its zone, or empty when there is no such account
     */
    Optional<ZoneId> timeZoneOf(UUID accountId);

    /**
     * The state an account is in.
     *
     * <p>One read for what the initial screen needs, rather than three: asking the status, the
     * undecided purposes and the reaffirmation separately would be three queries for one
     * screen, which is the shape rule R7 exists to prevent.
     *
     * @param accountId account
     * @return its state, or empty when there is no such account
     */
    Optional<AccountStateView> stateOf(UUID accountId);
}
