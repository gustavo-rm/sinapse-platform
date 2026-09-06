package br.com.sinapse.platform.curriculum.internal.persistence;

import br.com.sinapse.platform.curriculum.internal.domain.CatalogImport;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Applied imports of the curated catalogue. Read-only: nothing in this version writes one. */
public interface CatalogImportRepository extends JpaRepository<CatalogImport, UUID> {

    /**
     * The most recently applied import.
     *
     * <p>Ordered by the instant it was applied, which is what {@code ix_catalog_import_applied}
     * exists for.
     *
     * @return the latest import, or empty when the catalogue has never been imported
     */
    Optional<CatalogImport> findFirstByOrderByAppliedAtDesc();
}
