package br.com.sinapse.platform.educational.internal.persistence;

import br.com.sinapse.platform.educational.internal.domain.Teacher;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Teachers. */
public interface TeacherRepository extends JpaRepository<Teacher, UUID> {

    /**
     * The teacher record of an account.
     *
     * @param accountId account holding the teacher role
     * @return the record, if the account has one
     */
    Optional<Teacher> findByAccountId(UUID accountId);
}
