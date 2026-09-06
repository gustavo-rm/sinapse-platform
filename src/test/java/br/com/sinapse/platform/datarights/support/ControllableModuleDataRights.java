package br.com.sinapse.platform.datarights.support;

import br.com.sinapse.platform.shared.datarights.ModuleDataRights;
import br.com.sinapse.platform.shared.datarights.ModuleExport;
import java.util.UUID;

/**
 * A module that can be made to fail in the middle of an erasure.
 *
 * <p>The only way to test the property that matters most: partial erasure is worse than none, so
 * a failure has to take every write of every other module with it. Ordered between the modules
 * that do the deleting and the one that empties the account, so that when it throws there is
 * already plenty to roll back.
 *
 * <p>It does nothing at all when it is not failing, so its presence changes no other test.
 */
public class ControllableModuleDataRights implements ModuleDataRights {

    /** Between educational and identity: after the deletions, before the account is emptied. */
    public static final int ORDER = 50;

    private volatile boolean failing;

    @Override
    public String moduleName() {
        return "test-probe";
    }

    @Override
    public void eraseFor(UUID accountId) {
        if (failing) {
            throw new IllegalStateException("a module failed part-way through the erasure");
        }
    }

    @Override
    public ModuleExport exportFor(UUID accountId) {
        return ModuleExport.empty(moduleName());
    }

    /**
     * @param failing whether the next erasure should break part-way through
     */
    public void setFailing(boolean failing) {
        this.failing = failing;
    }
}
