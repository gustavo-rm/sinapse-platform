package br.com.sinapse.platform.planning.orchestration.instances;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The generator of the real-catalogue instances under {@code src/test/resources/instances/}.
 *
 * <p>Two modes. By default it compares: every instance is generated in memory and has to match
 * the committed file byte for byte, and nothing is written. With
 * {@code -Dsinapse.instances.regenerate=true} it writes the files instead, which is the only way
 * they are meant to change:
 *
 * <pre>
 * ./mvnw test -Dtest=RealCatalogInstanceGeneratorTest -Dsinapse.instances.regenerate=true
 * </pre>
 *
 * <p>After a regeneration the SHA-256 column of the README in that directory has to be updated
 * by hand; {@link RealCatalogInstancesTest} fails until it is.
 */
class RealCatalogInstanceGeneratorTest extends RealCatalogInstanceSupport {

    private static final Logger LOG = LoggerFactory.getLogger(RealCatalogInstanceGeneratorTest.class);

    @Test
    void theCommittedInstancesAreWhatTheProductionPathProduces() throws IOException {
        Map<String, byte[]> generated = generateAll();

        if (Boolean.getBoolean(REGENERATE_PROPERTY)) {
            Files.createDirectories(INSTANCES);
            for (Map.Entry<String, byte[]> instance : generated.entrySet()) {
                Path target = INSTANCES.resolve(instance.getKey());
                Files.write(target, instance.getValue());
                LOG.info("Regenerated {} (sha256 {})", target, sha256(instance.getValue()));
            }
            return;
        }

        for (Map.Entry<String, byte[]> instance : generated.entrySet()) {
            Path committed = INSTANCES.resolve(instance.getKey());
            assertThat(committed)
                    .as("%s is committed; regenerate with -D%s=true", committed,
                            REGENERATE_PROPERTY)
                    .exists();
            assertThat(Files.readAllBytes(committed))
                    .as("%s differs from what the production path assembles today; if the "
                            + "change is intended, regenerate with -D%s=true and update the "
                            + "README", committed, REGENERATE_PROPERTY)
                    .isEqualTo(instance.getValue());
        }
    }
}
