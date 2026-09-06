package br.com.sinapse.platform.shared.datarights;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything one module holds about one account, ready to be serialised.
 *
 * <p>Article 18 gives the holder confirmation and access, and access means their own data in a
 * form they can read and take elsewhere. Each module answers for what it stores, so the export
 * is assembled rather than written: the coordinator never learns what a study session is.
 *
 * <p>The collections are named by the module, and their contents are whatever that module
 * already publishes — its own view records, which serialise as they are. Nothing here
 * constrains the shape, because a shape imposed from {@code shared} would be a second copy of
 * every module's model.
 *
 * @param module      name of the module answering, used as the key in the assembled document
 * @param collections what it holds, by collection name. Empty when it holds nothing about this
 *                    account, which is a real and expected answer
 */
public record ModuleExport(String module, Map<String, List<Object>> collections) {

    /** Defensive copies, preserving the order the module put them in. */
    public ModuleExport {
        Map<String, List<Object>> copy = new LinkedHashMap<>();
        collections.forEach((name, rows) -> copy.put(name, List.copyOf(rows)));
        collections = Map.copyOf(copy);
    }

    /**
     * An export of nothing, for a module that holds nothing about this account.
     *
     * @param module name of the module answering
     * @return the empty export
     */
    public static ModuleExport empty(String module) {
        return new ModuleExport(module, Map.of());
    }
}
