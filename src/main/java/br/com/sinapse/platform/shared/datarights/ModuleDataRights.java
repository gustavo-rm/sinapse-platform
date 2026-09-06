package br.com.sinapse.platform.shared.datarights;

import java.util.UUID;

/**
 * What a module owes the holder of an account: the ability to take their data and the ability
 * to have it removed.
 *
 * <p><strong>The logic stays in the module.</strong> ADR 0011 is explicit that the coordinator
 * only sequences these calls inside one transaction and never touches another module's tables:
 * the moment it writes SQL against {@code study_session}, the rule that a context's logic lives
 * in its context is gone and the coordinator has become a service that knows the whole schema.
 *
 * <p><strong>A module that holds personal data and does not implement this is a silent
 * leak.</strong> It will happen the first time someone adds a module in a hurry, which is why
 * {@code EveryModuleWithPersonalDataParticipatesTest} derives the list of modules that owe an
 * implementation from the foreign keys in the schema rather than from a list somebody has to
 * remember to update.
 *
 * <p>Implementations declare an {@link org.springframework.core.annotation.Order}. The
 * coordinator runs them in that order, which is chosen so that a row is never deleted before
 * the rows referencing it.
 *
 * <p>Neither method checks authorisation. The coordinator has established whose account this is
 * before either is called.
 */
public interface ModuleDataRights {

    /**
     * The name this module answers under.
     *
     * @return a stable key for the assembled export and for the coverage test's message
     */
    String moduleName();

    /**
     * Removes what this module holds about the account.
     *
     * <p>Called inside the erasure transaction, with the erasure flag already set. What is
     * removed and what survives is the module's own decision, taken from the table in ADR 0011;
     * a module that keeps something has to be able to say why.
     *
     * <p>Must be idempotent: an erasure that failed part-way is rolled back and retried whole.
     *
     * @param accountId holder whose data is being erased
     */
    void eraseFor(UUID accountId);

    /**
     * Everything this module holds about the account.
     *
     * @param accountId holder asking for their own data
     * @return what it holds, possibly nothing
     */
    ModuleExport exportFor(UUID accountId);
}
