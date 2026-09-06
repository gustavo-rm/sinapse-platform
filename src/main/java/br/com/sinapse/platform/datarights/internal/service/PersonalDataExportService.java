package br.com.sinapse.platform.datarights.internal.service;

import br.com.sinapse.platform.shared.datarights.ModuleDataRights;
import br.com.sinapse.platform.shared.datarights.ModuleExport;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembles everything the platform holds about one holder.
 *
 * <p>Article 18's rights of confirmation and access. The document is the union of what each
 * module answers for its own data, keyed by module, and this class never learns what any of it
 * means — the moment it did, it would be a service that knows the whole schema.
 *
 * <p><strong>It is not gated on whether the data may still be processed.</strong> Every other
 * use case in the platform asks {@code AccountAccessPolicy} first, and this one deliberately
 * does not: a holder who has withdrawn their consent, or who has asked to be erased and is
 * waiting out the window, has more reason to want their own data than anyone, not less. The
 * only thing that has to be true is that they are asking about themselves, and the route
 * establishes that before this is reached.
 */
@Service
public class PersonalDataExportService {

    private final List<ModuleDataRights> modules;
    private final Clock clock;

    /**
     * @param modules every module that answers for its own data
     * @param clock   application clock
     */
    public PersonalDataExportService(List<ModuleDataRights> modules, Clock clock) {
        this.modules = List.copyOf(modules);
        this.clock = clock;
    }

    /**
     * Everything the platform holds about a holder.
     *
     * @param accountId holder asking for their own data
     * @return the assembled document
     */
    @Transactional(readOnly = true)
    public PersonalDataExport exportFor(UUID accountId) {
        Map<String, Map<String, List<Object>>> byModule = new LinkedHashMap<>();
        for (ModuleDataRights module : modules) {
            ModuleExport export = module.exportFor(accountId);
            byModule.put(export.module(), export.collections());
        }
        return new PersonalDataExport(clock.instant(), byModule);
    }

    /**
     * The assembled document.
     *
     * @param generatedAt when it was put together, so the holder knows what it is a picture of
     * @param modules     what each module holds, by module and then by collection
     */
    public record PersonalDataExport(Instant generatedAt,
            Map<String, Map<String, List<Object>>> modules) {
    }
}
