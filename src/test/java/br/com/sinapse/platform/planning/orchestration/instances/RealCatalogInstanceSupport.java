package br.com.sinapse.platform.planning.orchestration.instances;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.sinapse.platform.coreclient.contract.PlanRequest;
import br.com.sinapse.platform.curation.internal.CatalogApplier;
import br.com.sinapse.platform.curation.internal.CatalogFiles;
import br.com.sinapse.platform.curation.internal.model.DesiredCatalogue;
import br.com.sinapse.platform.curriculum.api.CurriculumCatalog;
import br.com.sinapse.platform.curriculum.api.SubjectView;
import br.com.sinapse.platform.curriculum.api.TopicView;
import br.com.sinapse.platform.identity.internal.domain.Account;
import br.com.sinapse.platform.planning.internal.config.PlanningProperties;
import br.com.sinapse.platform.planning.internal.service.GenerationRequestService.ClaimedJob;
import br.com.sinapse.platform.planning.orchestration.SnapshotAssembler;
import br.com.sinapse.platform.planning.orchestration.support.OrchestrationIntegrationTest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.Separators;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Builds the real-catalogue instances: synthetic {@link PlanRequest}s assembled from the
 * catalogue under {@code catalog/} through the production path, for diagnosing the optimiser
 * against exactly the input the product produces.
 *
 * <p><strong>What is real.</strong> The catalogue is applied with the importer an operator runs
 * ({@link CatalogApplier}); the student is registered and activated through the identity
 * services; availability and the goal are declared through the planning services; the job is
 * queued and claimed through {@code GenerationRequestService}, which is what computes the
 * horizon; and the document is built by {@link SnapshotAssembler}. Only the call to the core is
 * left out.
 *
 * <p><strong>What is pinned.</strong> The application clock is fixed at the horizon anchor, the
 * seed is a constant, and the algorithm parameters are the ones of the base
 * {@code application.yml}, read from it rather than copied, because the test profile lowers
 * them to keep the suite fast and an instance carrying three generations would describe a run
 * nobody makes.
 *
 * <p><strong>What is rewritten after assembly, and why.</strong> The curriculum assigns random
 * identifiers to subjects and topics, and the edge query has no order. Byte-identical output
 * therefore needs one canonicalisation step, applied to the assembled document and to nothing
 * else: every subject and topic identifier is replaced by a name-based UUID derived from its
 * catalogue code, edges are sorted by the curricular position of their endpoints, and map keys
 * are written in alphabetical order. This is a relabelling: no value other than an identifier
 * changes, no element is added or dropped, and an identifier that is neither a subject nor a
 * topic of the catalogue fails the generation instead of passing through.
 */
@Import(RealCatalogInstanceSupport.FixedClockConfiguration.class)
abstract class RealCatalogInstanceSupport extends OrchestrationIntegrationTest {

    /** Monday the horizon starts on, and the instant the application clock is fixed at. */
    static final Instant ANCHOR = Instant.parse("2026-10-12T00:00:00Z");

    /** Seed every instance carries. Chosen once; it has no meaning beyond being constant. */
    static final long SEED = 20261012L;

    /** Where the instances live in the source tree. */
    static final Path INSTANCES = Path.of("src", "test", "resources", "instances");

    /** The catalogue directory the importer reads, relative to the project root. */
    static final Path CATALOG = Path.of("catalog");

    /** System property that switches the generator from comparison to regeneration. */
    static final String REGENERATE_PROPERTY = "sinapse.instances.regenerate";

    /** Relative tolerance on the availability-to-effort ratio of each profile. */
    static final double RHO_TOLERANCE = 0.05;

    /** Source revision the generator records its catalogue imports under. */
    private static final String IMPORT_REVISION = "real-catalog-instances";

    /** Prefix of the parameters every run asks the optimiser for. */
    private static final String ALGORITHM_PARAMS = "sinapse.planning.generation.algorithm-params.";

    /** Namespace of the name-based identifiers that replace the curriculum's random ones. */
    private static final String NAMESPACE = "sinapse-real-catalog-instance";

    /**
     * The three profiles. They differ in availability and in nothing else.
     *
     * <p>Windows are local times in the student's zone, {@code America/Sao_Paulo} (UTC-3, no
     * daylight saving), and none of them reaches 21:00 local, so no interval crosses midnight
     * UTC.
     */
    static final List<Profile> PROFILES = List.of(
            new Profile("apertado", 0.35, List.of(
                    new Window(DayOfWeek.TUESDAY, LocalTime.of(19, 30), LocalTime.of(20, 30)),
                    new Window(DayOfWeek.THURSDAY, LocalTime.of(19, 30), LocalTime.of(20, 30)),
                    new Window(DayOfWeek.SATURDAY, LocalTime.of(9, 0), LocalTime.of(10, 10)))),
            new Profile("medio", 0.75, List.of(
                    new Window(DayOfWeek.MONDAY, LocalTime.of(19, 0), LocalTime.of(20, 0)),
                    new Window(DayOfWeek.TUESDAY, LocalTime.of(19, 0), LocalTime.of(20, 0)),
                    new Window(DayOfWeek.WEDNESDAY, LocalTime.of(19, 0), LocalTime.of(20, 0)),
                    new Window(DayOfWeek.THURSDAY, LocalTime.of(19, 0), LocalTime.of(20, 0)),
                    new Window(DayOfWeek.FRIDAY, LocalTime.of(19, 0), LocalTime.of(20, 0)),
                    new Window(DayOfWeek.SATURDAY, LocalTime.of(9, 0), LocalTime.of(10, 30)))),
            new Profile("folgado", 1.5, List.of(
                    new Window(DayOfWeek.MONDAY, LocalTime.of(18, 30), LocalTime.of(20, 30)),
                    new Window(DayOfWeek.TUESDAY, LocalTime.of(18, 30), LocalTime.of(20, 30)),
                    new Window(DayOfWeek.WEDNESDAY, LocalTime.of(18, 30), LocalTime.of(20, 30)),
                    new Window(DayOfWeek.THURSDAY, LocalTime.of(18, 30), LocalTime.of(20, 30)),
                    new Window(DayOfWeek.FRIDAY, LocalTime.of(18, 30), LocalTime.of(20, 30)),
                    new Window(DayOfWeek.SATURDAY, LocalTime.of(9, 0), LocalTime.of(11, 0)),
                    new Window(DayOfWeek.SUNDAY, LocalTime.of(14, 0), LocalTime.of(15, 0)))));

    @Autowired
    protected SnapshotAssembler assembler;

    @Autowired
    protected CatalogApplier applier;

    @Autowired
    protected CurriculumCatalog catalog;

    @Autowired
    protected PlanningProperties planningProperties;

    @Autowired
    protected ObjectMapper objectMapper;

    /**
     * Restores the production algorithm parameters over the test profile's lowered ones.
     *
     * <p>Read from the base {@code application.yml} and registered with their parsed types, so
     * that a number stays a number on the wire exactly as it does in production.
     */
    @DynamicPropertySource
    static void productionAlgorithmParams(DynamicPropertyRegistry registry) {
        productionAlgorithmParams().forEach((name, value) -> registry.add(name, () -> value));
    }

    /**
     * @return the algorithm parameters the base configuration declares, by full property name
     */
    static Map<String, Object> productionAlgorithmParams() {
        try {
            List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                    .load("application", new ClassPathResource("application.yml"));
            Map<String, Object> params = new LinkedHashMap<>();
            for (PropertySource<?> source : sources) {
                for (String name : ((EnumerablePropertySource<?>) source).getPropertyNames()) {
                    if (name.startsWith(ALGORITHM_PARAMS)) {
                        params.put(name, source.getProperty(name));
                    }
                }
            }
            assertThat(params).as("application.yml declares the algorithm parameters").isNotEmpty();
            return params;
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /**
     * Removes what the generation left behind.
     *
     * <p>The imports are recorded at the fixed clock, a week or more away from the instant the
     * rest of the suite runs at, and a test that reads "the latest import" by its time would
     * otherwise find one of these instead of its own.
     */
    @AfterEach
    void removeGeneratedState() {
        jdbc.execute("truncate table account, subject cascade");
        jdbc.update("delete from catalog_import where source_revision = ?", IMPORT_REVISION);
    }

    /**
     * Generates every profile.
     *
     * @return the serialised instances, by file name, in profile order
     */
    protected Map<String, byte[]> generateAll() {
        Map<String, byte[]> generated = new LinkedHashMap<>();
        for (Profile profile : PROFILES) {
            generated.put(profile.fileName(), serialise(generate(profile)));
        }
        return generated;
    }

    /**
     * Builds one profile's document through the production path and canonicalises it.
     *
     * <p>Starts from an empty curriculum and no accounts every time, so that one profile's state
     * cannot leak into the next.
     *
     * @param profile the availability profile
     * @return the canonical document
     */
    protected PlanRequest generate(Profile profile) {
        jdbc.execute("truncate table account, subject cascade");

        DesiredCatalogue desired = CatalogFiles.read(CATALOG, null);
        assertThat(desired.problems()).as("the catalogue files are valid").isEmpty();
        applier.apply(desired, IMPORT_REVISION, false);

        LocalDate start = LocalDate.ofInstant(ANCHOR, ZoneOffset.UTC);
        LocalDate end = start.plus(planningProperties.generation().horizon());

        Account student = student();
        for (Window window : profile.windows()) {
            availability.declare(student.id(), window.day(), window.start(), window.end(), start,
                    null);
        }
        List<SubjectView> subjects = subjectsInCodeOrder();
        for (int index = 0; index < subjects.size(); index++) {
            goals.set(student.id(), subjects.get(index).id(), end, priorityOf(index + 1));
        }

        requestService.queue(student.id(), null);
        List<ClaimedJob> claimed = requestService.claimNext();
        assertThat(claimed).as("the job just queued, and only it").hasSize(1);
        ClaimedJob job = claimed.getFirst();
        assertThat(job.horizonStart()).isEqualTo(start);
        assertThat(job.horizonEnd())
                .as("the horizon is the one production computes, from the configured period")
                .isEqualTo(end);

        seeds.setSeed(SEED);
        PlanRequest assembled = assembler.assemble(job, seeds.next());
        return canonical(assembled, subjects);
    }

    /**
     * Priority of a goal: {@code 1 + (ordinal mod 5)}, where the ordinal is the subject's
     * 1-based place in catalogue-code order.
     *
     * @param ordinal 1-based ordinal of the subject
     * @return the priority, 1 to 5
     */
    static int priorityOf(int ordinal) {
        return 1 + (ordinal % 5);
    }

    /**
     * Writes a document the way the instances are stored: the application's own mapper, with
     * map keys sorted and a fixed pretty printer, LF line endings and a final newline.
     *
     * @param request the document
     * @return its bytes
     */
    protected byte[] serialise(PlanRequest request) {
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter(
                Separators.createDefaultInstance()
                        .withObjectFieldValueSpacing(Separators.Spacing.AFTER))
                .withArrayIndenter(new DefaultIndenter("  ", "\n"))
                .withObjectIndenter(new DefaultIndenter("  ", "\n"));
        try {
            String json = objectMapper.copy()
                    .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                    .writer(printer)
                    .writeValueAsString(request);
            return (json + "\n").getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException(failure);
        }
    }

    /**
     * Availability over effort: total minutes of availability in the horizon divided by the sum
     * of the topics' estimated minutes.
     *
     * @param request the document
     * @return the ratio
     */
    static double rho(PlanRequest request) {
        return (double) availableMinutes(request) / estimatedMinutes(request);
    }

    /** Total minutes of availability the document offers. */
    static long availableMinutes(PlanRequest request) {
        return request.availability().stream()
                .mapToLong(slot -> Duration.between(slot.start(), slot.end()).toMinutes())
                .sum();
    }

    /** Sum of the topics' estimated minutes. */
    static long estimatedMinutes(PlanRequest request) {
        return request.topics().stream().mapToLong(PlanRequest.Topic::estimatedMinutes).sum();
    }

    /** SHA-256 of a byte array, in lowercase hexadecimal. */
    static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private List<SubjectView> subjectsInCodeOrder() {
        return catalog.subjects().stream()
                .sorted(Comparator.comparing(SubjectView::code))
                .toList();
    }

    /**
     * Replaces the curriculum's random identifiers with name-based ones and puts the unordered
     * collections in a fixed order. See the class comment for why this is the only rewriting.
     */
    private PlanRequest canonical(PlanRequest assembled, List<SubjectView> subjects) {
        Map<UUID, UUID> relabel = new HashMap<>();
        Map<UUID, String> subjectCode = new HashMap<>();
        Map<UUID, TopicView> topicById = new HashMap<>();
        for (SubjectView subject : subjects) {
            relabel.put(subject.id(), nameBased("subject/" + subject.code()));
            subjectCode.put(subject.id(), subject.code());
        }
        for (TopicView topic : catalog.topicsOfSubjects(subjectCode.keySet())) {
            relabel.put(topic.id(),
                    nameBased("topic/" + subjectCode.get(topic.subjectId()) + ":" + topic.code()));
            topicById.put(topic.id(), topic);
        }
        Set<UUID> labels = new HashSet<>(relabel.values());
        assertThat(labels).as("the relabelling is injective").hasSize(relabel.size());

        Comparator<UUID> curricular = Comparator
                .comparing((UUID id) -> subjectCode.get(topicById.get(id).subjectId()))
                .thenComparingInt(id -> topicById.get(id).position());

        List<PlanRequest.Goal> goals = assembled.goals().stream()
                .sorted(Comparator.comparing(goal -> subjectCode.get(goal.subjectId())))
                .map(goal -> new PlanRequest.Goal(map(relabel, goal.subjectId()),
                        goal.targetDate(), goal.priority()))
                .toList();
        List<PlanRequest.Topic> topics = assembled.topics().stream()
                .sorted(Comparator.comparing(PlanRequest.Topic::id, curricular))
                .map(topic -> new PlanRequest.Topic(map(relabel, topic.id()),
                        map(relabel, topic.subjectId()), topic.position(), topic.effortTier(),
                        topic.estimatedMinutes()))
                .toList();
        List<PlanRequest.PrerequisiteEdge> edges = assembled.prerequisites().stream()
                .sorted(Comparator.comparing(PlanRequest.PrerequisiteEdge::prerequisiteTopicId,
                                curricular)
                        .thenComparing(PlanRequest.PrerequisiteEdge::dependentTopicId, curricular))
                .map(edge -> new PlanRequest.PrerequisiteEdge(
                        map(relabel, edge.prerequisiteTopicId()),
                        map(relabel, edge.dependentTopicId()), edge.strength(),
                        edge.provenance()))
                .toList();
        assertThat(assembled.history())
                .as("a first plan: the student has no history, so nothing to relabel there")
                .isEmpty();

        return new PlanRequest(assembled.contractVersion(), assembled.horizon(),
                assembled.availability(), goals, topics, edges, assembled.history(),
                assembled.algorithmParams(), assembled.randomSeed());
    }

    private static UUID map(Map<UUID, UUID> relabel, UUID id) {
        UUID mapped = relabel.get(id);
        if (mapped == null) {
            throw new IllegalStateException(
                    "the assembled document carries an identifier that is neither a subject nor "
                            + "a topic of the catalogue");
        }
        return mapped;
    }

    private static UUID nameBased(String name) {
        return UUID.nameUUIDFromBytes((NAMESPACE + "/" + name).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Name-based identifiers of everything the catalogue holds, for the tests that check an
     * instance refers only to catalogue content.
     *
     * @param desired the catalogue as read from its files
     * @return subject identifiers and topic identifiers, as the instances write them
     */
    static Set<UUID> canonicalIdentifiersOf(DesiredCatalogue desired) {
        Set<UUID> identifiers = desired.subjects().keySet().stream()
                .map(code -> nameBased("subject/" + code))
                .collect(Collectors.toCollection(HashSet::new));
        desired.topics().forEach(topic -> identifiers.add(nameBased(
                "topic/" + topic.key().subjectCode() + ":" + topic.key().topicCode())));
        return identifiers;
    }

    /**
     * One availability profile.
     *
     * @param name      suffix of the file name
     * @param targetRho the ratio of available to estimated minutes it aims at
     * @param windows   the weekly routine, in the student's zone
     */
    record Profile(String name, double targetRho, List<Window> windows) {

        String fileName() {
            return "plan-request-catalogo-real-" + name + ".json";
        }
    }

    /**
     * A weekly window.
     *
     * @param day   day it recurs on
     * @param start when it opens, local time
     * @param end   when it closes, local time
     */
    record Window(DayOfWeek day, LocalTime start, LocalTime end) {
    }

    /** Fixes the application clock at the horizon anchor. */
    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedInstanceClock() {
            return Clock.fixed(ANCHOR, ZoneOffset.UTC);
        }
    }
}
