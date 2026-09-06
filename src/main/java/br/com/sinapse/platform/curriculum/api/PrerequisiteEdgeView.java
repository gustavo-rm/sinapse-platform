package br.com.sinapse.platform.curriculum.api;

import java.util.UUID;

/**
 * A directed prerequisite edge, as other modules see it.
 *
 * <p>The direction is fixed and worth stating once: {@code prerequisiteTopicId} is studied
 * <em>before</em> {@code dependentTopicId}.
 *
 * <p>{@code strength} and {@code provenance} both travel to the optimisation core.
 * The first tells it which mechanism to apply; the second is what makes the ablation
 * experiment possible.
 *
 * @param id                  identifier of the edge
 * @param prerequisiteTopicId topic that comes first
 * @param dependentTopicId    topic that depends on it
 * @param strength            constraint or penalty
 * @param provenance          where the edge came from
 * @param sourceReference     free-text citation of the source, when there is one
 */
public record PrerequisiteEdgeView(
        UUID id,
        UUID prerequisiteTopicId,
        UUID dependentTopicId,
        EdgeStrength strength,
        EdgeProvenance provenance,
        String sourceReference) {
}
