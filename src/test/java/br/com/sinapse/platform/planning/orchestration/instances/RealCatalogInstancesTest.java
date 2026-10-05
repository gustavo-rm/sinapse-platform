package br.com.sinapse.platform.planning.orchestration.instances;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import br.com.sinapse.platform.coreclient.contract.PlanRequest;
import br.com.sinapse.platform.curation.internal.CatalogFiles;
import br.com.sinapse.platform.curation.internal.model.DesiredCatalogue;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * What the committed real-catalogue instances promise to whoever diagnoses the optimiser with
 * them.
 *
 * <p>Each file is read back with the application's own mapper into the contract record, has to
 * cover the whole catalogue and refer to nothing else, has to be exactly what the production
 * path assembles today, and has to carry the checksum the README in its directory declares.
 */
class RealCatalogInstancesTest extends RealCatalogInstanceSupport {

    private static final Pattern UUID_TEXT =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private static final Pattern README_HEADING = Pattern.compile("^###\\s.*`([^`]+\\.json)`");

    private static final Pattern README_SHA = Pattern.compile("^\\|\\s*SHA-256\\s*\\|\\s*`([0-9a-f]{64})`");

    static Stream<Profile> profiles() {
        return PROFILES.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("profiles")
    void readsBackIntoTheContractWithTheApplicationsMapper(Profile profile) throws IOException {
        byte[] committed = committed(profile);

        PlanRequest request = objectMapper.readValue(committed, PlanRequest.class);

        assertThat(request.contractVersion()).isEqualTo(PlanRequest.VERSION);
        assertThat(request.horizon()).isNotNull();
        assertThat(request.availability()).isNotEmpty().doesNotContainNull();
        assertThat(request.goals()).isNotEmpty().doesNotContainNull();
        assertThat(request.topics()).isNotEmpty().doesNotContainNull();
        assertThat(request.prerequisites()).doesNotContainNull();
        assertThat(request.algorithmParams()).isNotEmpty();
        assertThat(serialise(request))
                .as("nothing is lost or added on the way through the record")
                .isEqualTo(committed);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("profiles")
    void coversTheWholeCatalogueAndRefersToNothingElse(Profile profile) throws IOException {
        DesiredCatalogue desired = CatalogFiles.read(CATALOG, null);
        PlanRequest request = read(profile);

        assertThat(request.topics())
                .as("every topic of the catalogue is in scope")
                .hasSameSizeAs(desired.topics());
        Set<UUID> topicIds = request.topics().stream()
                .map(PlanRequest.Topic::id)
                .collect(Collectors.toSet());
        Set<UUID> subjectIds = request.topics().stream()
                .map(PlanRequest.Topic::subjectId)
                .collect(Collectors.toSet());
        assertThat(topicIds).hasSameSizeAs(request.topics());
        assertThat(request.goals())
                .as("goals are per subject; every goal names a subject whose topics are sent")
                .allSatisfy(goal -> assertThat(subjectIds).contains(goal.subjectId()));
        assertThat(subjectIds)
                .as("and every subject sent is pursued by a goal")
                .isEqualTo(request.goals().stream()
                        .map(PlanRequest.Goal::subjectId)
                        .collect(Collectors.toSet()));
        assertThat(request.prerequisites())
                .hasSameSizeAs(desired.edges())
                .allSatisfy(edge -> assertThat(topicIds)
                        .contains(edge.prerequisiteTopicId(), edge.dependentTopicId()));
        assertThat(request.topics())
                .as("an estimate of zero would make the capacity diagnosis meaningless")
                .allSatisfy(topic -> assertThat(topic.estimatedMinutes()).isPositive());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("profiles")
    void carriesNoIdentifierOfAPerson(Profile profile) throws IOException {
        String text = new String(committed(profile), StandardCharsets.UTF_8);
        Set<UUID> catalogue = canonicalIdentifiersOf(CatalogFiles.read(CATALOG, null));

        Matcher uuids = UUID_TEXT.matcher(text);
        while (uuids.find()) {
            assertThat(catalogue)
                    .as("every identifier in the file is a subject or topic of the catalogue")
                    .contains(UUID.fromString(uuids.group()));
        }
        assertThat(text).doesNotContain("@");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("profiles")
    void isAFirstPlanOverTheDefaultHorizon(Profile profile) throws IOException {
        PlanRequest request = read(profile);
        LocalDate start = LocalDate.ofInstant(ANCHOR, ZoneOffset.UTC);

        assertThat(request.horizon().start()).isEqualTo(start);
        assertThat(request.horizon().end())
                .isEqualTo(start.plus(planningProperties.generation().horizon()));
        assertThat(request.history()).as("no history: the first-plan scenario").isEmpty();
        assertThat(request.goals()).allSatisfy(goal -> {
            assertThat(goal.targetDate()).isEqualTo(request.horizon().end());
            assertThat(goal.priority()).isBetween(1, 5);
        });
        assertThat(request.randomSeed()).isEqualTo(SEED);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("profiles")
    void meetsItsAvailabilityRatioWithoutCrossingMidnightUtc(Profile profile) throws IOException {
        PlanRequest request = read(profile);

        assertThat(rho(request) / profile.targetRho())
                .as("rho of %s: %d available over %d estimated", profile.name(),
                        availableMinutes(request), estimatedMinutes(request))
                .isCloseTo(1.0, within(RHO_TOLERANCE));
        assertThat(request.availability()).allSatisfy(slot -> {
            assertThat(slot.end()).isAfter(slot.start());
            assertThat(LocalDate.ofInstant(slot.end(), ZoneOffset.UTC))
                    .as("the midnight boundary is covered elsewhere; no interval crosses it here")
                    .isEqualTo(LocalDate.ofInstant(slot.start(), ZoneOffset.UTC));
            assertThat(slot.start()).isAfterOrEqualTo(ANCHOR);
        });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("profiles")
    void matchesARegenerationByteForByte(Profile profile) throws IOException {
        assertThat(serialise(generate(profile))).isEqualTo(committed(profile));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("profiles")
    void hasTheChecksumTheReadmeDeclares(Profile profile) throws IOException {
        assertThat(readmeChecksums())
                .containsEntry(profile.fileName(), sha256(committed(profile)));
    }

    @Test
    void generatingTwiceProducesTheSameBytes() {
        assertThat(generateAll()).usingRecursiveComparison().isEqualTo(generateAll());
    }

    @Test
    void theProfilesDifferOnlyInAvailability() throws IOException {
        List<PlanRequest> requests = PROFILES.stream().map(this::readUnchecked).toList();
        PlanRequest first = requests.getFirst();

        assertThat(requests).allSatisfy(request -> assertThat(request)
                .usingRecursiveComparison()
                .ignoringFields("availability")
                .isEqualTo(first));
        assertThat(requests.stream().map(PlanRequest::availability).distinct()).hasSize(3);
    }

    private byte[] committed(Profile profile) throws IOException {
        return Files.readAllBytes(INSTANCES.resolve(profile.fileName()));
    }

    private PlanRequest read(Profile profile) throws IOException {
        return objectMapper.readValue(committed(profile), PlanRequest.class);
    }

    private PlanRequest readUnchecked(Profile profile) {
        try {
            return read(profile);
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    /** The checksum declared under each file's heading in the README. */
    private static Map<String, String> readmeChecksums() throws IOException {
        Map<String, String> checksums = new HashMap<>();
        String current = null;
        for (String line : Files.readAllLines(INSTANCES.resolve("README.md"))) {
            Matcher heading = README_HEADING.matcher(line);
            if (heading.find()) {
                current = heading.group(1);
                continue;
            }
            Matcher sha = README_SHA.matcher(line);
            if (current != null && sha.find()) {
                checksums.put(current, sha.group(1));
            }
        }
        return checksums;
    }
}
