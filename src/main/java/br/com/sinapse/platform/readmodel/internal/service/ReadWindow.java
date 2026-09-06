package br.com.sinapse.platform.readmodel.internal.service;

import br.com.sinapse.platform.readmodel.internal.config.ReadModelProperties;
import br.com.sinapse.platform.readmodel.internal.error.InvalidReadWindowException;
import br.com.sinapse.platform.shared.web.TimeWindows;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * The one place a requested window is accepted or refused for a composed read.
 *
 * <p>The rule is {@link TimeWindows}, shared with every module that answers a windowed read;
 * what belongs here is the ceiling. It is this component's own, and narrower than any single
 * module's, because a composed read asks several modules for the same window at once — the
 * span that costs one query in the learning record costs four here.
 */
@Component
public class ReadWindow {

    private final Duration maxSpan;

    /**
     * @param properties configured ceilings of the read models
     */
    public ReadWindow(ReadModelProperties properties) {
        this.maxSpan = properties.maxWindow();
    }

    /**
     * Refuses a window these reads will not answer.
     *
     * @param from start, inclusive
     * @param to   end, exclusive
     * @throws InvalidReadWindowException if the window is empty, inverted or too wide
     */
    public void require(Instant from, Instant to) {
        if (!TimeWindows.isAcceptable(from, to, maxSpan)) {
            throw new InvalidReadWindowException();
        }
    }
}
