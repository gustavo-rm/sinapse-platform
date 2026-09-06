package br.com.sinapse.platform.learningrecord.internal.persistence;

import java.util.UUID;

/**
 * One row of the effort aggregation, as the database produces it.
 *
 * <p>Minutes and counts rather than a {@code Duration}, because that is what a JPQL
 * constructor expression can build. Turning it into the api type is the service's job, and
 * keeping the two apart is what stops the shape of a query from leaking into a published DTO.
 *
 * @param topicId      topic
 * @param minutes      total effective minutes on it
 * @param sessionCount how many sessions produced that total
 */
public record TopicEffortRow(UUID topicId, long minutes, long sessionCount) {
}
