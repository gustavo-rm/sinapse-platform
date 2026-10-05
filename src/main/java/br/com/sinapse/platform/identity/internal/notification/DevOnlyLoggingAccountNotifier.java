package br.com.sinapse.platform.identity.internal.notification;

import br.com.sinapse.platform.identity.internal.web.IdentityRoutes;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * DEV ONLY. Writes the token a holder would have received to the log, so that an account
 * registered on a workstation can be activated through the API without touching the database.
 *
 * <p><strong>This is the one place in the platform that writes a credential to a log, and
 * the exception is deliberate.</strong> The token is single-use and authorises the operation on
 * its own, which is why {@link AccountNotifier} forbids logging it and why
 * {@link LoggingAccountNotifier} drops it. The exception holds only because of three
 * conditions together: the profile is {@code local}, a developer's workstation; the accounts are
 * synthetic, created by the developer who reads the log; and the log is the console of a
 * process on that workstation, which nothing collects or keeps. Take away any one of them and
 * this class is a leak.
 *
 * <p>So it fails closed. It exists only under the {@code local} profile, and it refuses to start
 * when any other profile is active alongside: a deployment that switched on {@code local} by
 * mistake next to its own profile gets a failed startup rather than credentials in its logs.
 * Production runs with no profile active, and there this bean does not exist.
 *
 * <p>The holder's address is not written, not even here: the developer knows it, and the
 * account identifier is enough to tell two registrations apart. Nothing exposes the token over
 * HTTP; the log is the only way out.
 */
@Component
@Primary
@Profile(DevOnlyLoggingAccountNotifier.LOCAL_PROFILE)
public class DevOnlyLoggingAccountNotifier implements AccountNotifier {

    /** The only profile this notifier may run under, and it must run alone. */
    static final String LOCAL_PROFILE = "local";

    private static final Logger LOG = LoggerFactory.getLogger(DevOnlyLoggingAccountNotifier.class);

    /**
     * @param environment the active profiles, checked so that this never runs next to another one
     * @throws IllegalStateException if any profile besides {@code local} is active
     */
    public DevOnlyLoggingAccountNotifier(Environment environment) {
        List<String> active = Arrays.asList(environment.getActiveProfiles());
        if (!active.equals(List.of(LOCAL_PROFILE))) {
            throw new IllegalStateException("The DEV ONLY notifier writes credentials to the log and "
                    + "runs only under the local profile alone; active profiles: " + active);
        }
    }

    @Override
    public void deliver(AccountNotification notification) {
        LOG.info("DEV ONLY - not a delivery. Token for account {}, purpose {}, valid until {}. "
                        + "Redeem with: POST {} {}",
                notification.accountId(), notification.purpose(), notification.expiresAt(),
                routeOf(notification), bodyOf(notification));
    }

    private static String routeOf(AccountNotification notification) {
        return switch (notification.purpose()) {
            case EMAIL_VERIFICATION -> IdentityRoutes.EMAIL_VERIFICATIONS;
            case PASSWORD_RESET -> IdentityRoutes.PASSWORD_RESET_CONFIRMATION;
            case MAJORITY_REAFFIRMATION -> IdentityRoutes.CONSENT_REAFFIRMATION;
        };
    }

    private static String bodyOf(AccountNotification notification) {
        return switch (notification.purpose()) {
            case PASSWORD_RESET -> "{\"token\":\"" + notification.token()
                    + "\",\"newPassword\":\"<new password>\"}";
            case EMAIL_VERIFICATION, MAJORITY_REAFFIRMATION ->
                    "{\"token\":\"" + notification.token() + "\"}";
        };
    }
}
