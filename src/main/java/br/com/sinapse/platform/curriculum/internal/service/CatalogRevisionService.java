package br.com.sinapse.platform.curriculum.internal.service;

import br.com.sinapse.platform.curriculum.api.CatalogRevisions;
import br.com.sinapse.platform.curriculum.internal.domain.CatalogImport;
import br.com.sinapse.platform.curriculum.internal.persistence.CatalogImportRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Answers which curated state of the catalogue is in effect. */
@Service
@Transactional(readOnly = true)
public class CatalogRevisionService implements CatalogRevisions {

    private final CatalogImportRepository imports;

    /**
     * @param imports applied imports of the catalogue
     */
    public CatalogRevisionService(CatalogImportRepository imports) {
        this.imports = imports;
    }

    @Override
    public Optional<UUID> currentRevisionId() {
        return imports.findFirstByOrderByAppliedAtDesc().map(CatalogImport::id);
    }
}
