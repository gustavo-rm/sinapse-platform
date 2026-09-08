package br.com.sinapse.platform.curation.internal.model;

import br.com.sinapse.platform.curriculum.api.EffortTier;

/**
 * A topic as the file describes it.
 *
 * @param key        natural key
 * @param name       display name
 * @param position   curricular ordering within the subject
 * @param effortTier ordinal effort band, never minutes: what the minutes are is configuration,
 *                   so that they can be calibrated against observation instead of asserted by
 *                   whoever curated (ADR 0012)
 * @param line       line of {@code topics.csv} it came from
 */
public record DesiredTopic(TopicKey key, String name, int position, EffortTier effortTier, int line) {
}
