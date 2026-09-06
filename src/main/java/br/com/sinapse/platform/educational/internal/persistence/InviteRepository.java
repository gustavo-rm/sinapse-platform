package br.com.sinapse.platform.educational.internal.persistence;

import br.com.sinapse.platform.educational.internal.domain.Invite;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Invites. */
public interface InviteRepository extends JpaRepository<Invite, UUID> {

    /**
     * An invite by its canonical code.
     *
     * <p>Codes are unique across the whole table, expired and revoked ones included, so that a
     * code is never ambiguous even historically.
     *
     * @param code canonical, uppercase code
     * @return the invite, if the code was ever issued
     */
    Optional<Invite> findByCode(String code);

    /**
     * Whether a code has ever been issued.
     *
     * <p>Read when generating one. The unique index is the real guarantee; this only keeps the
     * generator from handing back a duplicate it could have avoided.
     *
     * @param code candidate code
     * @return whether it is taken
     */
    boolean existsByCode(String code);

    /**
     * The invites of a classroom, newest first.
     *
     * @param classroomId classroom
     * @return its invites, including spent and revoked ones
     */
    List<Invite> findByClassroomIdOrderByCreatedAtDesc(UUID classroomId);

    /**
     * The invites of a classroom that the teacher has not revoked.
     *
     * <p>Read when archiving. Expired and exhausted ones are included: revoking them is a
     * no-op for redeemability and keeps the table honest about the fact that the classroom was
     * closed rather than that the codes ran out.
     *
     * @param classroomId classroom
     * @return its unrevoked invites
     */
    List<Invite> findByClassroomIdAndRevokedAtIsNull(UUID classroomId);
}
