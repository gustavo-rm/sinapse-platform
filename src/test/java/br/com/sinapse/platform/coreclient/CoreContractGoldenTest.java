package br.com.sinapse.platform.coreclient;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.sinapse.platform.IntegrationTest;
import br.com.sinapse.platform.coreclient.contract.PlanRequest;
import br.com.sinapse.platform.coreclient.contract.PlanResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;

/**
 * Pins the wire shape of the Core contract to a reference document.
 *
 * <p><strong>Why a golden file and not a shared Maven module.</strong> ADR 0007 called for the
 * contract to live in a versioned artifact consumed by both repositories. That was not built,
 * and ADR 0015 decides not to build it: each side keeps its own copy of the records and both
 * validate serialisation against an identical reference document. The trade is deliberate —
 * publishing costs nothing, and the price is that a divergence is caught by this build rather
 * than by the compiler.
 *
 * <p>The reference documents under {@code src/test/resources/contract/} are byte-for-byte the
 * ones {@code exam-optimizer-application} keeps. A change to either side's records that this
 * test does not also see applied to the documents is the divergence this test exists to find.
 *
 * <p><strong>The mapper is the application's own bean, not a fresh one.</strong> The shape on
 * the wire is decided as much by configuration as by the records: {@code non_null} inclusion
 * decides whether a null field appears at all, and {@code write-dates-as-timestamps: false}
 * decides whether an instant is a string or a number. A {@code new ObjectMapper()} here would
 * assert a configuration that does not exist in production, which is why this test pays for the
 * full context rather than a slice — {@code @JsonTest} would not apply
 * {@code TimeConfiguration}'s customiser.
 *
 * <p>Two mechanisms together make the check complete, because neither is alone:
 *
 * <ol>
 *   <li>the round trip, compared over the <em>union</em> of the keys of both trees, catches a
 *       renamed field, a retyped field, any value drift and a <em>removed</em> field: a key the
 *       reference document has and the record no longer writes back is reported as missing;</li>
 *   <li>the component sweep catches an <em>added</em> field. Nothing else would: under
 *       {@code non_null} a new nullable component is simply absent from the output, so the
 *       round trip stays green while the two repositories have already diverged.</li>
 * </ol>
 *
 * <p><strong>Deserialisation does not reject an unknown property, and is not meant to.</strong>
 * The application's mapper leaves {@code FAIL_ON_UNKNOWN_PROPERTIES} off, which is Spring Boot's
 * default, so a key the record does not have is ignored on read. That is what lets an additive
 * field from the core not take the platform down; a removed field is caught by the key walk
 * above, not by the read.
 *
 * <p>The wire shape is configuration as well as records: {@code spring.jackson.*} (here,
 * {@code default-property-inclusion: non_null}) changes it without touching a record, so a change
 * there needs the same version bump and the same re-sync as a change to a record (ADR 0016).
 */
class CoreContractGoldenTest extends IntegrationTest {

    /** Reference document of the request, identical in the optimiser's repository. */
    private static final String REQUEST_GOLDEN = "contract/plan-request-v1.0.json";

    /** Reference document of the response, identical in the optimiser's repository. */
    private static final String RESPONSE_GOLDEN = "contract/plan-response-v1.0.json";

    /**
     * What to do about a failure. Appended to every assertion here, because the useful
     * instruction is never "make the test pass" — it is that the shape changed and the other
     * repository does not know yet.
     */
    private static final String REMINDER =
            "The Core contract's wire shape no longer matches its reference document. "
                    + "If the change is intended (a record, or spring.jackson.* configuration): "
                    + "bump PlanRequest.VERSION and re-sync the golden file in "
                    + "exam-optimizer-application, in the same logical change.";

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void theRequestRecordSerialisesToItsReferenceDocument() throws IOException {
        assertRoundTrip(REQUEST_GOLDEN, PlanRequest.class);
    }

    @Test
    void theResponseRecordSerialisesToItsReferenceDocument() throws IOException {
        assertRoundTrip(RESPONSE_GOLDEN, PlanResponse.class);
    }

    @Test
    void everyComponentOfEveryRecordIsExercisedByTheReferenceDocuments() throws IOException {
        assertEveryComponentCovered(REQUEST_GOLDEN, PlanRequest.class);
        assertEveryComponentCovered(RESPONSE_GOLDEN, PlanResponse.class);
    }

    /**
     * The version in the documents is the version the code is at.
     *
     * <p>A reference document written against another version is worse than none: it would go on
     * passing while describing a shape nobody sends.
     */
    @Test
    void theReferenceDocumentsDeclareTheVersionTheContractIsAt() throws IOException {
        assertThat(read(REQUEST_GOLDEN, PlanRequest.class).contractVersion())
                .as("plan-request golden vs PlanRequest.VERSION. " + REMINDER)
                .isEqualTo(PlanRequest.VERSION);
        assertThat(read(RESPONSE_GOLDEN, PlanResponse.class).contractVersion())
                .as("plan-response golden vs PlanRequest.VERSION. " + REMINDER)
                .isEqualTo(PlanRequest.VERSION);
    }

    /**
     * The two documents are one example, not two.
     *
     * <p>{@code RestSinapseCore} rejects a response whose seed is not the seed that was sent, so
     * a pair that disagreed on it could never occur in a run and would be a misleading thing for
     * the optimiser to test against.
     */
    @Test
    void theTwoReferenceDocumentsAreOneConsistentExchange() throws IOException {
        PlanRequest request = read(REQUEST_GOLDEN, PlanRequest.class);
        PlanResponse response = read(RESPONSE_GOLDEN, PlanResponse.class);

        assertThat(response.metadata().randomSeed())
                .as("the response echoes the seed the request sent")
                .isEqualTo(request.randomSeed());

        Set<UUID> planned = request.topics().stream()
                .map(PlanRequest.Topic::id)
                .collect(Collectors.toSet());
        assertThat(response.sessions()).allSatisfy(session ->
                assertThat(planned)
                        .as("every scheduled session names a topic the request sent")
                        .contains(session.topicId()));
    }

    /**
     * The four effort bands, one per topic.
     *
     * <p>{@code effortTier} crosses the wire as a {@code String} and there is no enum in the
     * contract package to enumerate it, so this document is the only executable record of the
     * closed set. A consumer that reads it knows the four values it must accept — and, by
     * ADR 0015, that it must reject a fifth rather than assume a default.
     */
    @Test
    void theRequestCarriesEveryEffortTierAsAString() throws IOException {
        PlanRequest request = read(REQUEST_GOLDEN, PlanRequest.class);

        assertThat(request.topics()).extracting(PlanRequest.Topic::effortTier)
                .as("the closed set of effort bands, as strings")
                .containsExactlyInAnyOrder("SHORT", "STANDARD", "LONG", "EXTENDED");

        JsonNode golden = tree(REQUEST_GOLDEN);
        assertThat(golden.get("topics")).allSatisfy(topic ->
                assertThat(topic.get("effortTier").isTextual())
                        .as("effortTier is a JSON string, not an enum token or a number")
                        .isTrue());
    }

    /**
     * The two shapes a consumer gets wrong.
     *
     * <p>Both say "nothing is known about this topic", and they are not the same thing. A topic
     * absent from {@code history} had no session inside the configured window at all — never
     * studied, or last studied before the window opens. A topic present with no
     * {@code lastStudiedAt} and no ratings did have a session in the window, but none that
     * closed with a recorded duration. The document carries both so that the optimiser has an
     * example of each to be tested against.
     */
    @Test
    void theRequestCarriesATopicWithNoHistoryAndATopicMissingFromHistoryAltogether()
            throws IOException {

        PlanRequest request = read(REQUEST_GOLDEN, PlanRequest.class);

        assertThat(request.history())
                .as("a topic that is in history but has neither a last study nor any rating")
                .anySatisfy(entry -> {
                    assertThat(entry.lastStudiedAt()).isNull();
                    assertThat(entry.recallRatings()).isEmpty();
                    assertThat(entry.sessionCount()).isZero();
                });

        Set<UUID> withHistory = request.history().stream()
                .map(PlanRequest.TopicHistory::topicId)
                .collect(Collectors.toSet());
        assertThat(request.topics()).extracting(PlanRequest.Topic::id)
                .as("a topic that has no history entry at all")
                .anySatisfy(id -> assertThat(withHistory).doesNotContain(id));

        JsonNode entry = tree(REQUEST_GOLDEN).get("history").get(2);
        assertThat(entry.has("lastStudiedAt"))
                .as("the key is absent, not present and null: non_null inclusion omits it")
                .isFalse();
        assertThat(entry.get("recallRatings"))
                .as("recallRatings is an empty array and never absent, because the record "
                        + "copies it defensively and a missing key would fail to bind")
                .isEmpty();
    }

    // -----------------------------------------------------------------------------------
    // Machinery
    // -----------------------------------------------------------------------------------

    /**
     * Reads the document into the record, writes the record back out, and compares the two
     * trees field by field.
     *
     * <p>Key order is not compared: it is decided by the record's declaration order and is not
     * part of what either side promises. Presence is compared in both directions, because a
     * field the other repository does not know about is exactly the divergence being looked for.
     */
    private void assertRoundTrip(String resource, Class<?> type) throws IOException {
        JsonNode expected = tree(resource);
        JsonNode actual = objectMapper.readTree(objectMapper.writeValueAsString(read(resource, type)));

        List<String> differences = new ArrayList<>();
        compare("", expected, actual, differences);

        assertThat(differences)
                .as("%s, deserialised into %s and serialised back. %s",
                        resource, type.getSimpleName(), REMINDER)
                .isEmpty();
    }

    /** Walks both trees together and names every place they disagree. */
    private static void compare(String path, JsonNode expected, JsonNode actual,
            List<String> differences) {

        if (expected.isObject() && actual.isObject()) {
            for (String field : union(expected, actual)) {
                String child = path.isEmpty() ? field : path + "." + field;
                if (!actual.has(field)) {
                    differences.add(child + ": missing from the serialised record, present in "
                            + "the reference document as " + abbreviate(expected.get(field)));
                } else if (!expected.has(field)) {
                    differences.add(child + ": present in the serialised record as "
                            + abbreviate(actual.get(field)) + ", absent from the reference "
                            + "document");
                } else {
                    compare(child, expected.get(field), actual.get(field), differences);
                }
            }
            return;
        }
        if (expected.isArray() && actual.isArray()) {
            if (expected.size() != actual.size()) {
                differences.add(path + ": the reference document has " + expected.size()
                        + " element(s), the serialised record has " + actual.size());
                return;
            }
            for (int index = 0; index < expected.size(); index++) {
                compare(path + "[" + index + "]", expected.get(index), actual.get(index),
                        differences);
            }
            return;
        }
        if (!sameValue(expected, actual)) {
            differences.add(path + ": the reference document has " + abbreviate(expected)
                    + ", the serialised record has " + abbreviate(actual));
        }
    }

    /**
     * Value equality, with one concession to Jackson's node types.
     *
     * <p>A {@code long} component writes a {@code LongNode} while the same literal parses out of
     * the document as an {@code IntNode} when it fits in an int, and those two are not equal to
     * each other however equal the numbers are. Integral and fractional are still held apart:
     * {@code 0} turning into {@code 0.0} is a shape change and is reported.
     */
    private static boolean sameValue(JsonNode expected, JsonNode actual) {
        if (expected.isNumber() && actual.isNumber()) {
            return expected.isIntegralNumber() == actual.isIntegralNumber()
                    && expected.decimalValue().compareTo(actual.decimalValue()) == 0;
        }
        return expected.equals(actual);
    }

    /**
     * Fails when a record declares a component the reference documents never exercise.
     *
     * <p>This is the check that catches an added field. The others cannot: a new component that
     * is null in the canonical instance is omitted from the output entirely under
     * {@code non_null}, so the round trip compares two documents that agree and says nothing.
     *
     * <p>Components are gathered per record type across every occurrence of that type, so a
     * component deliberately absent from one instance — {@code lastStudiedAt} on the topic with
     * no closed session — is covered by the instances that do carry it.
     */
    private void assertEveryComponentCovered(String resource, Class<?> type) throws IOException {
        Map<Class<?>, Set<String>> observed = new HashMap<>();
        collect(type, tree(resource), observed);

        observed.forEach((recordType, keys) -> {
            Set<String> uncovered = Arrays.stream(recordType.getRecordComponents())
                    .map(RecordComponent::getName)
                    .filter(name -> !keys.contains(name))
                    .collect(Collectors.toCollection(TreeSet::new));
            assertThat(uncovered)
                    .as("%s declares component(s) that %s never exercises, so a consumer reading "
                            + "the document would never see them. %s",
                            recordType.getSimpleName(), resource, REMINDER)
                    .isEmpty();
        });
    }

    /** Walks the document guided by the record types, noting which keys each type is seen with. */
    private static void collect(Type type, JsonNode node, Map<Class<?>, Set<String>> observed) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return;
        }
        Class<?> raw = rawTypeOf(type);
        if (raw != null && raw.isRecord() && node.isObject()) {
            Set<String> keys = observed.computeIfAbsent(raw, key -> new LinkedHashSet<>());
            node.fieldNames().forEachRemaining(keys::add);
            for (RecordComponent component : raw.getRecordComponents()) {
                collect(component.getGenericType(), node.get(component.getName()), observed);
            }
            return;
        }
        // A Map component is opaque by contract — algorithmParams and fitness are not
        // interpreted by this side — so nothing below it is a shape either repository promises.
        if (node.isArray() && type instanceof ParameterizedType parameterized) {
            Type[] arguments = parameterized.getActualTypeArguments();
            for (JsonNode element : node) {
                collect(arguments[arguments.length - 1], element, observed);
            }
        }
    }

    private static Class<?> rawTypeOf(Type type) {
        if (type instanceof Class<?> raw) {
            return raw;
        }
        if (type instanceof ParameterizedType parameterized
                && parameterized.getRawType() instanceof Class<?> raw) {
            return raw;
        }
        return null;
    }

    private static Set<String> union(JsonNode expected, JsonNode actual) {
        Set<String> fields = new LinkedHashSet<>();
        for (Iterator<String> names = expected.fieldNames(); names.hasNext(); ) {
            fields.add(names.next());
        }
        for (Iterator<String> names = actual.fieldNames(); names.hasNext(); ) {
            fields.add(names.next());
        }
        return fields;
    }

    /** Keeps a failure message to one line when the divergence is a whole nested object. */
    private static String abbreviate(JsonNode node) {
        String rendered = node.toString();
        return rendered.length() <= 120 ? rendered : rendered.substring(0, 117) + "...";
    }

    private <T> T read(String resource, Class<T> type) throws IOException {
        try (InputStream stream = new ClassPathResource(resource).getInputStream()) {
            return objectMapper.readValue(stream, type);
        }
    }

    private JsonNode tree(String resource) throws IOException {
        try (InputStream stream = new ClassPathResource(resource).getInputStream()) {
            return objectMapper.readTree(stream);
        }
    }
}
