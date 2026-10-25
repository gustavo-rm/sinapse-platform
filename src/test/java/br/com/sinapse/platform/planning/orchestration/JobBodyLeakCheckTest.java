package br.com.sinapse.platform.planning.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * {@link JobBodyLeakCheck} gives no false positive on the digits a job body carries by chance, and
 * still catches the core's status, text or address wherever a field could carry them.
 */
class JobBodyLeakCheckTest {

    private static final String[] CORE_TEXTS =
            {"127.0.0.1", "http", "unknown-engine", "no-such-engine", "exception"};

    private final ObjectMapper mapper = new ObjectMapper();

    /** A failed job whose identifier and timestamps all contain {@code 422}. */
    private static final String UNLUCKY_DIGITS = """
            {"id":"4220c3f1-9a42-4422-8422-0a1b2c3d4422",
             "status":"FAILED",
             "horizonStart":"2026-10-21","horizonEnd":"2026-11-18",
             "requestedAt":"2026-10-21T14:08:51.422Z",
             "startedAt":"2026-10-21T14:08:51.994226Z",
             "finishedAt":"2026-10-21T14:22:42.004221Z",
             "attemptCount":1,
             "failureReason":"CORE_REJECTED"}
            """;

    @Test
    void theStatusDigitsInsideTheIdentifierAndTheTimestampsAreNotALeak() {
        assertThat(UNLUCKY_DIGITS).as("the old whole-body search would have failed here")
                .contains("422");

        assertThatCode(() -> JobBodyLeakCheck.assertTellsNothingAboutTheCore(
                body(UNLUCKY_DIGITS), 422, CORE_TEXTS))
                .doesNotThrowAnyException();
    }

    @Test
    void theCoreStatusInTheFieldThatNamesTheFailureIsALeak() {
        String leaking = UNLUCKY_DIGITS.replace("\"CORE_REJECTED\"",
                "\"CORE_REJECTED: core answered 422\"");

        assertThatThrownBy(() -> JobBodyLeakCheck.assertTellsNothingAboutTheCore(
                body(leaking), 422, CORE_TEXTS))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("what member 'failureReason' says of the core")
                .hasMessageContaining("422");
    }

    @Test
    void aMessageMemberTheRecordDoesNotHaveIsALeakWhateverItSays() {
        String leaking = UNLUCKY_DIGITS.replace("\"attemptCount\":1,",
                "\"attemptCount\":1,\"message\":\"422 unknown-engine\",");

        assertThatThrownBy(() -> JobBodyLeakCheck.assertTellsNothingAboutTheCore(
                body(leaking), 422, CORE_TEXTS))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("member 'message' is one of GenerationRequestResponse's");
    }

    @Test
    void anIdentifierOrATimestampCannotSmuggleText() {
        String inId = UNLUCKY_DIGITS.replace("\"4220c3f1-9a42-4422-8422-0a1b2c3d4422\"",
                "\"core said 422\"");
        String inInstant = UNLUCKY_DIGITS.replace("\"2026-10-21T14:08:51.994226Z\"",
                "\"2026-10-21T14:08:51.994226Z 422\"");

        assertThatThrownBy(() -> JobBodyLeakCheck.assertTellsNothingAboutTheCore(
                body(inId), 422, CORE_TEXTS))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("member 'id' parses as its type");
        assertThatThrownBy(() -> JobBodyLeakCheck.assertTellsNothingAboutTheCore(
                body(inInstant), 422, CORE_TEXTS))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("member 'startedAt' parses as its type");
    }

    @Test
    void aFailureReasonOutsideTheClosedSetIsRejected() {
        String unknown = UNLUCKY_DIGITS.replace("\"CORE_REJECTED\"", "\"ENGINE_REFUSED\"");

        assertThatThrownBy(() -> JobBodyLeakCheck.assertTellsNothingAboutTheCore(
                body(unknown), 422, CORE_TEXTS))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("member 'failureReason' is one of PlanGenerationFailure's");
    }

    @ParameterizedTest(name = "\"{0}\" -> [{1}]")
    @CsvSource(delimiter = '|', value = {
            "the core answered 422|422",
            "422|422",
            "(422)|422",
            "status=422;|422",
            "HTTP 422 Unprocessable|422,http",
            "Unknown-Engine: no-such-engine|unknown-engine,no-such-engine",
            "IllegalStateException at 127.0.0.1|127.0.0.1,exception",
            "51.994226Z|",
            "4220c3f1|",
            "14220|",
            "CORE_REJECTED|"})
    void theStatusIsFoundOnlyAsAnIsolatedTokenAndTheTextsIgnoringCase(String text,
            String expected) {
        String[] leaks = expected == null ? new String[0] : expected.split(",");

        assertThat(JobBodyLeakCheck.leaksIn(text, 422, CORE_TEXTS)).containsExactly(leaks);
    }

    private JsonNode body(String json) throws Exception {
        return mapper.readTree(json);
    }
}
