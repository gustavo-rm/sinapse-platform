package br.com.sinapse.platform.identity.internal.persistence;

import br.com.sinapse.platform.identity.api.ConsentPurpose;
import br.com.sinapse.platform.identity.internal.domain.ConsentRecord;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Consent records. Append-only: nothing here deletes. */
public interface ConsentRecordRepository extends JpaRepository<ConsentRecord, UUID> {

    /**
     * The consent currently in force for a purpose.
     *
     * @param accountId holder
     * @param purpose   purpose
     * @return the valid record, if there is one
     */
    Optional<ConsentRecord> findByAccountIdAndPurposeAndRevokedAtIsNull(UUID accountId, ConsentPurpose purpose);

    /**
     * Every consent currently in force for a holder.
     *
     * @param accountId holder
     * @return the valid records
     */
    List<ConsentRecord> findByAccountIdAndRevokedAtIsNull(UUID accountId);

    /**
     * Every consent in force for a set of holders, in one query.
     *
     * <p>Architecture rule R7: a module exposes lookups by set of identifiers and not only
     * by one, or composition above it produces one query per row.
     *
     * @param accountIds holders
     * @return the valid records of all of them
     */
    List<ConsentRecord> findByAccountIdInAndRevokedAtIsNull(Collection<UUID> accountIds);

    /**
     * The whole history of a set of holders, in one query.
     *
     * <p>Read by the daily majority sweep, which needs granted and revoked records alike:
     * the condition it evaluates is about whether an act of consent ever happened after a
     * date, not about what is in force now.
     *
     * @param accountIds holders
     * @return every record of all of them
     */
    List<ConsentRecord> findByAccountIdIn(Collection<UUID> accountIds);

    /**
     * The whole history of a holder, most recent act first.
     *
     * @param accountId holder
     * @return granted and revoked records alike
     */
    List<ConsentRecord> findByAccountIdOrderByGrantedAtDesc(UUID accountId);
}
