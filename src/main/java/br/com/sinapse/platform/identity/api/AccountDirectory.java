package br.com.sinapse.platform.identity.api;

import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

/**
 * The little that another context legitimately needs to know about an account.
 *
 * <p>Today that is one thing: the zone the holder's local times are read in. Availability is
 * declared as "Tuesday, seven to nine in the evening", and turning that into an instant on a
 * calendar requires the zone. The module that holds the account is the one that should answer
 * it, rather than every other context keeping a copy that drifts.
 *
 * <p>Deliberately narrow. This is not a place to publish an account: the name, the address and
 * the date of birth are personal data with no caller outside this module, and a directory that
 * returned them would become the way they leak. What is here is the zone, and it is here
 * because the snapshot assembly cannot be written without it.
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
}
