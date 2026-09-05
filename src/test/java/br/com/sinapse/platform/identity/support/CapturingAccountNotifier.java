package br.com.sinapse.platform.identity.support;

import br.com.sinapse.platform.identity.internal.domain.AccountTokenPurpose;
import br.com.sinapse.platform.identity.internal.notification.AccountNotification;
import br.com.sinapse.platform.identity.internal.notification.AccountNotifier;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Keeps what would have been delivered, so that a test can act as the account holder.
 *
 * <p>This is the only way to obtain a token from outside the module, and deliberately so:
 * the clear value never leaves the issuing service other than towards a notifier, so a test
 * that wants one has to stand where the holder stands.
 */
public class CapturingAccountNotifier implements AccountNotifier {

    private final List<AccountNotification> delivered = new CopyOnWriteArrayList<>();

    @Override
    public void deliver(AccountNotification notification) {
        delivered.add(notification);
    }

    /** Everything captured so far, in order. */
    public List<AccountNotification> delivered() {
        return List.copyOf(delivered);
    }

    /** Everything captured for one account and purpose. */
    public List<AccountNotification> deliveredTo(UUID accountId, AccountTokenPurpose purpose) {
        return delivered.stream()
                .filter(notification -> notification.accountId().equals(accountId))
                .filter(notification -> notification.purpose() == purpose)
                .toList();
    }

    /** The most recent token issued to an account for a purpose. */
    public Optional<String> lastToken(UUID accountId, AccountTokenPurpose purpose) {
        List<AccountNotification> matching = deliveredTo(accountId, purpose);
        return matching.isEmpty()
                ? Optional.empty()
                : Optional.of(matching.get(matching.size() - 1).token());
    }

    /** The most recent token issued to an account for a purpose, or a failed assertion. */
    public String requireToken(UUID accountId, AccountTokenPurpose purpose) {
        return lastToken(accountId, purpose).orElseThrow(() ->
                new AssertionError("No " + purpose + " token was delivered to account " + accountId));
    }

    /** Forgets everything, so that one test cannot see another test's deliveries. */
    public void clear() {
        delivered.clear();
    }
}
