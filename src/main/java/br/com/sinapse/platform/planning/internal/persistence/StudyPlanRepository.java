package br.com.sinapse.platform.planning.internal.persistence;

import br.com.sinapse.platform.planning.api.PlanStatus;
import br.com.sinapse.platform.planning.internal.domain.StudyPlan;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

/** Plans. Never deleted, and never edited beyond their supersession. */
public interface StudyPlanRepository extends JpaRepository<StudyPlan, UUID> {

    /**
     * The plan an account has in this state.
     *
     * <p>Asked with {@link PlanStatus#ACTIVE}, where the partial unique index guarantees there
     * is at most one.
     *
     * @param accountId student
     * @param status    state being looked for
     * @return the plan, if there is one
     */
    Optional<StudyPlan> findByAccountIdAndStatus(UUID accountId, PlanStatus status);

    /**
     * Every plan an account has had, newest first.
     *
     * <p>Capped rather than paged. Re-planning is manual (decision F6), so this list grows in
     * single figures; the limit is the hard server-side ceiling section 1 of the API contract
     * asks for on a list returned whole, not a page mechanism.
     *
     * @param accountId student
     * @param limit     ceiling on how many are returned
     * @return the plans, most recent first
     */
    List<StudyPlan> findByAccountIdOrderByCreatedAtDesc(UUID accountId, Limit limit);

    /**
     * The plan a generation job produced.
     *
     * <p>At most one: the foreign key to the job is unique. This is how a client that polled a
     * job to {@code READY} finds the plan it was waiting for.
     *
     * @param generationRequestId job
     * @return the plan it produced, if it produced one
     */
    Optional<StudyPlan> findByGenerationRequestId(UUID generationRequestId);
}
