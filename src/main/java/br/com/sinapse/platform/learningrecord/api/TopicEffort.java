package br.com.sinapse.platform.learningrecord.api;

import java.time.Duration;
import java.util.UUID;

/**
 * How much effective time an account spent on one topic within a window.
 *
 * <p>Only closed sessions with a recorded duration contribute. A session still running has
 * no duration yet, and counting the time elapsed so far would make the total depend on when
 * the question was asked.
 *
 * <p>The topic is an identifier and nothing more: the name belongs to the curriculum
 * catalogue, and joining it here would be this module reading another module's table. The
 * read models compose the two.
 *
 * @param topicId      topic
 * @param effort       total effective time on it
 * @param sessionCount how many closed sessions with a duration produced that total. Carried
 *                     because ninety minutes over six sessions and ninety minutes over one
 *                     are different evidence, and the total alone cannot tell them apart
 */
public record TopicEffort(UUID topicId, Duration effort, int sessionCount) {
}
