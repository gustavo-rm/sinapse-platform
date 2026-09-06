package br.com.sinapse.platform.learningrecord.internal.service;

import br.com.sinapse.platform.learningrecord.internal.config.LearningRecordProperties;
import br.com.sinapse.platform.learningrecord.internal.error.InvalidTimeWindowException;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * The one place a requested history window is accepted or refused.
 *
 * <p>Section 1 of the API contract: a history grows without limit, so the window is mandatory
 * and its span is capped, and there is no generic pagination to fall back on. The cap is
 * configuration rather than a constant, because the right number depends on how much a pilot
 * account accumulates and nobody knows that yet.
 *
 * <p>The window is half-open — {@code from} inclusive, {@code to} exclusive — which is why an
 * empty window is refused rather than answered with nothing: a client asking for a window
 * that cannot contain anything has made a mistake, and answering it with an empty list would
 * hide the mistake behind a plausible result.
 */
@Component
public class HistoryWindow {

    private final Duration maxSpan;

    /**
     * @param properties configured limits of this module
     */
    public HistoryWindow(LearningRecordProperties properties) {
        this.maxSpan = properties.maxHistoryWindow();
    }

    /**
     * Refuses a window the server will not answer.
     *
     * @param from start, inclusive
     * @param to   end, exclusive
     * @throws InvalidTimeWindowException if the window is empty, inverted or too wide
     */
    public void require(Instant from, Instant to) {
        if (!to.isAfter(from) || Duration.between(from, to).compareTo(maxSpan) > 0) {
            throw new InvalidTimeWindowException();
        }
    }
}
