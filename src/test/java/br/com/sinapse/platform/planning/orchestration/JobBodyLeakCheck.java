package br.com.sinapse.platform.planning.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.fasterxml.jackson.databind.JsonNode;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Checks that a polled job body tells the student nothing about the core, field by field.
 *
 * <p>Searching the whole body for the core's status code is not deterministic: the body carries a
 * random UUID and timestamps with microseconds, and {@code 422} turns up in them by chance. This
 * check reads the body against {@link GenerationRequestResponse}, the record the route
 * serialises, instead:
 *
 * <ul>
 *   <li>every member must be one of the record's components, so a new member that could carry
 *       the core's message fails here until it is typed;</li>
 *   <li>identifiers, instants and dates must parse as exactly that, so they cannot carry text,
 *       and they are the only values not searched;</li>
 *   <li>numbers must be numbers;</li>
 *   <li>everything else — the two enums today, any text tomorrow — is searched for the core's
 *       status as an isolated token (not surrounded by other digits) and for each of the core's
 *       texts, ignoring case; enum values must also be one of their constants.</li>
 * </ul>
 */
final class JobBodyLeakCheck {

    private static final Map<String, Class<?>> COMPONENTS = Arrays
            .stream(GenerationRequestResponse.class.getRecordComponents())
            .collect(Collectors.toMap(RecordComponent::getName, RecordComponent::getType));

    private JobBodyLeakCheck() {
    }

    /**
     * @param body       the polled job, as the route returned it
     * @param coreStatus the HTTP status the core answered with
     * @param coreTexts  anything else of the core's that must not reach the student: its address,
     *                   its error code, the engine name it refused
     */
    static void assertTellsNothingAboutTheCore(JsonNode body, int coreStatus, String... coreTexts) {
        assertThat(body.isObject()).as("the job body is a JSON object").isTrue();
        body.fields().forEachRemaining(member -> {
            String name = member.getKey();
            JsonNode value = member.getValue();
            assertThat(COMPONENTS)
                    .as("member '%s' is one of GenerationRequestResponse's components", name)
                    .containsKey(name);
            Class<?> type = COMPONENTS.get(name);
            if (type == UUID.class) {
                assertParses(name, value, text -> UUID.fromString(text).toString());
            } else if (type == Instant.class) {
                assertParses(name, value, text -> Instant.parse(text).toString());
            } else if (type == LocalDate.class) {
                assertParses(name, value, text -> LocalDate.parse(text).toString());
            } else if (type.isPrimitive() || Number.class.isAssignableFrom(type)) {
                assertThat(value.isNumber()).as("member '%s' is a number", name).isTrue();
            } else {
                assertThat(value.isTextual()).as("member '%s' is a single text value", name)
                        .isTrue();
                assertThat(leaksIn(value.asText(), coreStatus, coreTexts))
                        .as("what member '%s' says of the core", name)
                        .isEmpty();
                if (type.isEnum()) {
                    assertThat(Arrays.stream(type.getEnumConstants()).map(Object::toString))
                            .as("member '%s' is one of %s's constants", name, type.getSimpleName())
                            .contains(value.asText());
                }
            }
        });
    }

    /**
     * @return each piece of the core found in {@code text}: its status as an isolated token, and
     *         every one of its texts found ignoring case
     */
    static List<String> leaksIn(String text, int coreStatus, String... coreTexts) {
        List<String> found = new ArrayList<>();
        if (Pattern.compile("(?<!\\d)" + coreStatus + "(?!\\d)").matcher(text).find()) {
            found.add(Integer.toString(coreStatus));
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (String coreText : coreTexts) {
            if (lower.contains(coreText.toLowerCase(Locale.ROOT))) {
                found.add(coreText);
            }
        }
        return found;
    }

    /** A value that round-trips through its type's parser carries nothing but that type. */
    private static void assertParses(String name, JsonNode value, Function<String, String> parser) {
        assertThat(value.isTextual()).as("member '%s' is a single text value", name).isTrue();
        String text = value.asText();
        assertThatCode(() -> parser.apply(text))
                .as("member '%s' parses as its type", name)
                .doesNotThrowAnyException();
        assertThat(parser.apply(text)).as("member '%s' is exactly its type's form", name)
                .isEqualTo(text);
    }
}
