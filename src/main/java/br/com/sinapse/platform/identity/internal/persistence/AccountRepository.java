package br.com.sinapse.platform.identity.internal.persistence;

import br.com.sinapse.platform.identity.api.AccountStatus;
import br.com.sinapse.platform.identity.internal.domain.Account;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Accounts. */
public interface AccountRepository extends JpaRepository<Account, UUID> {

    /**
     * Finds an account by address, ignoring the ones that were anonymised.
     *
     * <p>The address of an erased account is deliberately free again, so it may exist more
     * than once in the table; only one row of a given address can be non-anonymised, which
     * the partial unique index guarantees. Matching is case-insensitive because the column
     * is {@code citext}, not because of anything this query does.
     *
     * @param email address to look for
     * @return the account still in use under that address
     */
    Optional<Account> findByEmailAndAnonymizedAtIsNull(String email);

    /**
     * Whether the address is taken.
     *
     * <p>Invariant 6, checked before writing so that the client gets an answer rather than
     * a constraint violation. The index remains the actual guarantee, because two
     * concurrent registrations both pass this check.
     *
     * @param email address to look for
     * @return whether a non-anonymised account already uses it
     */
    boolean existsByEmailAndAnonymizedAtIsNull(String email);

    /**
     * Coarse filter of the daily majority sweep: accounts in a given state whose date of
     * birth is old enough that they may have reached the threshold.
     *
     * <p>Deliberately approximate. Whether the holder has actually reached the threshold
     * depends on their own time zone, and pushing that into SQL would mean converting the
     * instant per row against a zone stored in the same row. The sweep narrows the
     * candidates here and decides per account, in the holder's zone.
     *
     * @param status         state the account has to be in
     * @param bornOnOrBefore latest date of birth that could already have reached the
     *                       threshold anywhere on Earth
     * @return the candidates
     */
    List<Account> findByStatusAndDateOfBirthLessThanEqual(AccountStatus status, LocalDate bornOnOrBefore);

    /**
     * Which of a set of accounts are in a given state.
     *
     * <p>Identifiers rather than rows, and one query rather than one per account (rule R7).
     * The caller is an access check over a whole classroom, and it wants a yes or a no per
     * student — loading forty accounts, every one of them carrying an address and a date of
     * birth, to read one column off each would be personal data brought into memory for
     * nothing.
     *
     * @param accountIds accounts to test
     * @param status     state they must be in
     * @return the identifiers of those that are
     */
    @Query("""
            select account.id
              from Account account
             where account.id in :accountIds
               and account.status = :status
            """)
    Set<UUID> findIdsByStatus(@Param("accountIds") Collection<UUID> accountIds,
            @Param("status") AccountStatus status);
}
