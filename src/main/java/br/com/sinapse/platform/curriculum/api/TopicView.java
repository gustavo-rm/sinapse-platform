package br.com.sinapse.platform.curriculum.api;

import java.util.UUID;

/**
 * A topic, as other modules see it.
 *
 * <p>A DTO and never the entity. Three contexts reference topics by identifier, and handing
 * them a managed entity would let them navigate wherever the mapping happens to allow,
 * turning the module boundary into decoration.
 *
 * @param id         identifier other contexts reference
 * @param subjectId  subject this topic belongs to
 * @param code       stable natural key, unique within the subject
 * @param name       display name
 * @param position   curricular ordering inside the subject
 * @param effortTier ordinal effort band, never minutes
 */
public record TopicView(
        UUID id,
        UUID subjectId,
        String code,
        String name,
        int position,
        EffortTier effortTier) {
}
