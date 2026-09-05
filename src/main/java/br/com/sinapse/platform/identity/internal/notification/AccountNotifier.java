package br.com.sinapse.platform.identity.internal.notification;

/**
 * Delivers a token to the account holder.
 *
 * <p>An implementation must treat everything it receives as a secret and as personal data:
 * the token authorises the operation on its own, and the address identifies a person.
 * Neither may reach a log.
 */
@FunctionalInterface
public interface AccountNotifier {

    /**
     * Sends the notification.
     *
     * @param notification token, address and what the token is for
     */
    void deliver(AccountNotification notification);
}
