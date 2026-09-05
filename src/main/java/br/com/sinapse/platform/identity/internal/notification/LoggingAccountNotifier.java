package br.com.sinapse.platform.identity.internal.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The adapter in place while the platform has no way of sending mail.
 *
 * <p>It records that a delivery was due and drops it. What it writes is the account
 * identifier and the purpose — an internal identifier and a constant. The token and the
 * address are not written, not even at debug level: a token in a log is a credential in a
 * log, and an address in a log is personal data in a log.
 *
 * <p><strong>Consequence, stated rather than hidden:</strong> with this adapter in place,
 * a holder never receives a verification link, so an account registered through the API
 * cannot be activated through the API. The flows are complete and tested; the transport is
 * not built yet, and building it was not part of this scope. A deployment that has a
 * transport contributes an {@link AccountNotifier} of its own, marked primary, and this
 * one stops being consulted.
 */
@Component
public class LoggingAccountNotifier implements AccountNotifier {

    private static final Logger LOG = LoggerFactory.getLogger(LoggingAccountNotifier.class);

    @Override
    public void deliver(AccountNotification notification) {
        LOG.info("Notification due for account {} with purpose {}; no transport is configured, "
                + "so it was discarded", notification.accountId(), notification.purpose());
    }
}
