package br.com.sinapse.platform.identity.api;

import java.util.UUID;

/**
 * The two account transitions a component outside identity may ask for.
 *
 * <p>Both belong to the data subject rights coordinator, and both belong to one flow: a request
 * to be erased suspends the account and revokes its sessions at once, and withdrawing that
 * request within the window puts it back. Neither can live in the coordinator, because an
 * account's state is identity's and there is exactly one thing in the platform that moves it.
 *
 * <p>Deliberately narrow. This is not a remote control for account state: activation after
 * e-mail verification, the suspension that follows withdrawing an essential consent, and the
 * terminal anonymisation all stay inside identity, where the rules that decide them live.
 */
public interface AccountLifecycle {

    /**
     * Suspends an account and revokes its sessions.
     *
     * <p>A suspended holder can still sign in and still exercise their rights over their own
     * data — that is what makes the seven-day window reversible — and can do nothing else,
     * because every other use case asks {@link AccountAccessPolicy} first and it answers false.
     *
     * @param accountId holder
     */
    void suspend(UUID accountId);

    /**
     * Restores a suspended account to active.
     *
     * <p>It re-asserts the consent invariant rather than assuming it: an account can be
     * suspended for more than one reason, and withdrawing an erasure request says nothing about
     * a consent that was withdrawn in the meantime.
     *
     * @param accountId holder
     */
    void reactivate(UUID accountId);
}
