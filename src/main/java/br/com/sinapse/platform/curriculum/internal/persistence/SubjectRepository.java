package br.com.sinapse.platform.curriculum.internal.persistence;

import br.com.sinapse.platform.curriculum.internal.domain.Subject;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Subjects. */
public interface SubjectRepository extends JpaRepository<Subject, UUID> {

    /**
     * Finds a subject by its stable code.
     *
     * @param code natural key
     * @return the subject, if the code is known
     */
    Optional<Subject> findByCode(String code);

    /**
     * The whole catalogue, by code.
     *
     * @return every subject
     */
    List<Subject> findAllByOrderByCodeAsc();
}
