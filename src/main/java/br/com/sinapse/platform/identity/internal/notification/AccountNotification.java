package br.com.sinapse.platform.identity.internal.notification;

import br.com.sinapse.platform.identity.internal.domain.AccountTokenPurpose;
import java.time.Instant;
import java.util.UUID;

/**
 * A token on its way to the account holder.
 *
 * <p>This is the one object in the module that carries a clear token value together with
 * an address. It exists for the duration of a call and is never persisted, never logged
 * and never returned by an endpoint.
 *
 * @param accountId holder the token belongs to
 * @param email     address to deliver to. Personal data
 * @param purpose   what the token authorises, which is what the message has to explain
 * @param token     the clear value. Whoever holds it can perform the operation
 * @param expiresAt when the value stops working, so the message can say so
 */
public record AccountNotification(
        UUID accountId,
        String email,
        AccountTokenPurpose purpose,
        String token,
        Instant expiresAt) {
}
