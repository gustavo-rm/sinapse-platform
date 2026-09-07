package br.com.sinapse.platform.readmodel.internal.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Ceilings on what these reads will answer, bound from {@code sinapse.read-model}.
 *
 * <p>They exist because section 1 of the API contract refuses a generic pagination mechanism
 * and puts two rules in its place: a history takes a mandatory window with a capped span, and a
 * naturally bounded list comes back whole with a hard server-side cap. Both numbers are
 * configuration rather than constants for the same reason every other limit in this system is —
 * nobody yet knows what a pilot classroom looks like after a term, and the right response to
 * finding out is an edit to a file, not to a class.
 *
 * @param maxWindow          widest window a composed read will answer. Narrower than the
 *                           per-module ceilings on purpose: a read model asks several modules
 *                           for the same window at once, so the same span costs more here than
 *                           it does in any one of them
 * @param maxRecentSessions  hard cap on the session list of a teacher's panel. The panel is a
 *                           summary; a student's full history is read from the history
 *                           endpoint, which has its own window
 * @param maxClassroomStudents hard cap on the class list. A classroom is naturally small, and
 *                           this is what stops "returned whole" from coming to mean "returned
 *                           however large it got"
 */
@Validated
@ConfigurationProperties("sinapse.read-model")
public record ReadModelProperties(

        @NotNull @DefaultValue("31d") Duration maxWindow,

        @Positive @DefaultValue("50") int maxRecentSessions,

        @Positive @DefaultValue("200") int maxClassroomStudents) {
}
