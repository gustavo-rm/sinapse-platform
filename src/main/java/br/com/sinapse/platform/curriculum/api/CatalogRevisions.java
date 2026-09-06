package br.com.sinapse.platform.curriculum.api;

import java.util.Optional;
import java.util.UUID;

/**
 * Which curated state of the catalogue is in effect.
 *
 * <p>Every application of the curated catalogue writes a row recording the git commit of the
 * files that produced it (ADR 0014). This publishes the latest of those, and it exists for one
 * caller: the generation job records it, so that an experimental result can be attributed to a
 * catalogue revision. The snapshot carries the edges themselves but not which revision they
 * came from, and without that the ablation experiment cannot say what it compared.
 *
 * <p>The importer that writes these rows is not part of this version. Until it runs there is no
 * revision, which is why the answer is optional rather than invented.
 */
public interface CatalogRevisions {

    /**
     * The most recently applied revision of the catalogue.
     *
     * @return its identifier, or empty when the catalogue has never been imported
     */
    Optional<UUID> currentRevisionId();
}
