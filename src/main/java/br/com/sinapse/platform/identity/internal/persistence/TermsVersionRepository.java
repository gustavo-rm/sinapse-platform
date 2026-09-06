package br.com.sinapse.platform.identity.internal.persistence;

import br.com.sinapse.platform.identity.api.ConsentPurpose;
import br.com.sinapse.platform.identity.internal.domain.TermsVersion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Published wordings of the consent terms. */
public interface TermsVersionRepository extends JpaRepository<TermsVersion, UUID> {

    /**
     * The wording in force for a purpose, which is the most recently published one.
     *
     * @param purpose purpose
     * @return the current wording, if any has ever been published
     */
    Optional<TermsVersion> findFirstByPurposeOrderByPublishedAtDesc(ConsentPurpose purpose);

    /**
     * Whether a wording with this label already exists for the purpose.
     *
     * @param purpose purpose
     * @param version label
     * @return whether it is already published
     */
    boolean existsByPurposeAndVersion(ConsentPurpose purpose, String version);

    /**
     * Every published wording, newest first.
     *
     * @return the catalogue
     */
    List<TermsVersion> findAllByOrderByPublishedAtDesc();
}
