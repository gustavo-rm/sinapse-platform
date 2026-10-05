package br.com.sinapse.platform.planning.orchestration.endtoend;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * The real optimisation core, for the end-to-end test, and nothing standing in for it.
 *
 * <p>Two ways to provide it, resolved once per JVM:
 *
 * <ol>
 *   <li><strong>A core already running.</strong> {@value #URL_VARIABLE} (or the system property
 *       {@value #URL_PROPERTY}) points at it, and nothing is started. This is the shape of a CI
 *       job that publishes the core as a service, and of a developer with one open in a
 *       terminal.</li>
 *   <li><strong>A core jar.</strong> {@value #JAR_VARIABLE} (or {@value #JAR_PROPERTY}) names the
 *       executable jar built from {@code exam-optimizer-application}, and it is started in a
 *       container with the {@value #CORE_PROFILE} profile — the one under which {@code POST /plans}
 *       exists at all. The core repository publishes no image, which is why the jar is run on a
 *       plain JRE image rather than pulled ready-made.</li>
 * </ol>
 *
 * <p>Neither set is a failure with a message saying so, never a skip. The test carries a tag that
 * keeps it out of the default build; once somebody asks for it, a missing core is the thing they
 * need to hear about, not a green run that tested nothing.
 *
 * <p><strong>Which engine answers</strong> is the one parameter that switches the experimental
 * condition: {@value #ENGINE_VARIABLE} or {@value #ENGINE_PROPERTY}, {@value #DEFAULT_ENGINE} when
 * neither is set. It travels as {@code algorithmParams.engine}, which is how the core chooses its
 * engine per request, so the same core answers both conditions.
 */
final class RealCore {

    static final String URL_VARIABLE = "SINAPSE_E2E_CORE_URL";
    static final String URL_PROPERTY = "sinapse.e2e.core.url";
    static final String JAR_VARIABLE = "SINAPSE_E2E_CORE_JAR";
    static final String JAR_PROPERTY = "sinapse.e2e.core.jar";
    static final String ENGINE_VARIABLE = "SINAPSE_E2E_CORE_ENGINE";
    static final String ENGINE_PROPERTY = "sinapse.e2e.core.engine";

    /** The deterministic heuristic, which is what the core answers with when asked nothing. */
    static final String DEFAULT_ENGINE = "greedy-baseline";

    /** The core's profile that registers {@code POST /plans}. */
    private static final String CORE_PROFILE = "baseline-core";

    private static final DockerImageName JRE = DockerImageName.parse("eclipse-temurin:21-jre");

    private static final int CORE_PORT = 8080;

    private static RealCore instance;

    private final String baseUrl;
    private final String engine;
    private final String origin;

    private RealCore(String baseUrl, String engine, String origin) {
        this.baseUrl = baseUrl;
        this.engine = engine;
        this.origin = origin;
    }

    /** The core for this JVM, started on first use. */
    static synchronized RealCore get() {
        if (instance == null) {
            instance = resolve();
        }
        return instance;
    }

    /** Where the core answers. */
    String baseUrl() {
        return baseUrl;
    }

    /** The engine every run asks for. */
    String engine() {
        return engine;
    }

    /** How the core was provided, for failure messages. */
    String origin() {
        return origin;
    }

    private static RealCore resolve() {
        String engine = setting(ENGINE_PROPERTY, ENGINE_VARIABLE);
        engine = engine == null ? DEFAULT_ENGINE : engine;

        String url = setting(URL_PROPERTY, URL_VARIABLE);
        if (url != null) {
            return new RealCore(url, engine, "the core already running at " + url);
        }

        String jar = setting(JAR_PROPERTY, JAR_VARIABLE);
        if (jar == null) {
            throw new IllegalStateException("The end-to-end test needs the real Sinapse Core. Set "
                    + URL_VARIABLE + " to a running core, or " + JAR_VARIABLE + " to the "
                    + "executable jar of exam-optimizer-application (./mvnw package there) to have "
                    + "it started in a container. Nothing stands in for the core in this test.");
        }
        Path path = Path.of(jar).toAbsolutePath();
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException(JAR_VARIABLE + " names " + path
                    + ", which is not a file.");
        }

        GenericContainer<?> core = new GenericContainer<>(JRE)
                .withCopyFileToContainer(MountableFile.forHostPath(path), "/app/core.jar")
                .withCommand("java", "-jar", "/app/core.jar",
                        "--spring.profiles.active=" + CORE_PROFILE,
                        "--server.port=" + CORE_PORT)
                .withExposedPorts(CORE_PORT)
                .waitingFor(Wait.forHttp("/actuator/health").forStatusCode(200)
                        .withStartupTimeout(Duration.ofMinutes(3)));
        core.start();
        String started = "http://" + core.getHost() + ":" + core.getMappedPort(CORE_PORT);
        return new RealCore(started, engine, "the core started from " + path + " at " + started);
    }

    private static String setting(String property, String variable) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) {
            value = System.getenv(variable);
        }
        return value == null || value.isBlank() ? null : value;
    }
}
