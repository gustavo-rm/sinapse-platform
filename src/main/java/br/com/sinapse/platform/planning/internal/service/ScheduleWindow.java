package br.com.sinapse.platform.planning.internal.service;

import br.com.sinapse.platform.planning.internal.config.PlanningProperties;
import br.com.sinapse.platform.planning.internal.error.InvalidTimeWindowException;
import br.com.sinapse.platform.shared.web.TimeWindows;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * The one place a requested schedule window is accepted or refused.
 *
 * <p>The rule is {@link TimeWindows}, shared with every module that answers a windowed read.
 * What belongs here is the ceiling, which is this module's own: planned sessions accumulate at
 * a different rate from anything else, and the right number is configuration because nobody
 * knows yet what a pilot account looks like after a term.
 */
@Component
public class ScheduleWindow {

    private final Duration maxSpan;

    /**
     * @param properties configured limits of this module
     */
    public ScheduleWindow(PlanningProperties properties) {
        this.maxSpan = properties.maxScheduleWindow();
    }

    /**
     * Refuses a window the server will not answer.
     *
     * @param from start, inclusive
     * @param to   end, exclusive
     * @throws InvalidTimeWindowException if the window is empty, inverted or too wide
     */
    public void require(Instant from, Instant to) {
        if (!TimeWindows.isAcceptable(from, to, maxSpan)) {
            throw new InvalidTimeWindowException();
        }
    }
}
