package br.com.sinapse.platform.learningrecord.internal.persistence;

import java.util.UUID;

/**
 * Total effective minutes of one account, as the database produces it.
 *
 * @param accountId student
 * @param minutes   total effective minutes in the window
 */
public record AccountEffortRow(UUID accountId, long minutes) {
}
