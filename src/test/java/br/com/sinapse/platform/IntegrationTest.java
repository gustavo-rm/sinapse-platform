package br.com.sinapse.platform;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class of every test that needs a database.
 *
 * <p>The container is started once for the whole JVM and shared by all subclasses, so
 * that the suite pays for PostgreSQL once rather than once per class. Reuse is requested
 * as well: with {@code testcontainers.reuse.enable=true} in the developer's
 * {@code ~/.testcontainers.properties}, or {@code TESTCONTAINERS_REUSE_ENABLE=true} in
 * the environment, the same container survives between builds. Without it the flag is
 * ignored and the container is simply created and disposed of as usual, which is what
 * continuous integration wants.
 *
 * <p>Anything that touches the schema belongs here, so that migrations run against a real
 * PostgreSQL rather than against an in-memory database that does not share its semantics.
 *
 * <p><strong>Externally provided PostgreSQL.</strong> Testcontainers needs a container
 * runtime, which is not available in every environment the build has to run in — a CI job
 * that already publishes PostgreSQL as a service, or a workstation without Docker. When
 * {@code SINAPSE_TEST_DB_URL} is set, that database is used as it is and no container is
 * started. The database still has to be a real PostgreSQL with the {@code citext}
 * extension available, because the migrations and the triggers are what is under test.
 * Nothing else changes: the same migrations run against it and the same assertions apply.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTest {

    /** Environment variable that points the suite at a PostgreSQL the build did not start. */
    private static final String EXTERNAL_URL = "SINAPSE_TEST_DB_URL";

    /** User of the externally provided database. */
    private static final String EXTERNAL_USERNAME = "SINAPSE_TEST_DB_USERNAME";

    /** Password of the externally provided database. */
    private static final String EXTERNAL_PASSWORD = "SINAPSE_TEST_DB_PASSWORD";

    private static final Database DATABASE = resolveDatabase();

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DATABASE::url);
        registry.add("spring.datasource.username", DATABASE::username);
        registry.add("spring.datasource.password", DATABASE::password);
    }

    private static Database resolveDatabase() {
        String url = System.getenv(EXTERNAL_URL);
        if (url != null && !url.isBlank()) {
            return new Database(url,
                    System.getenv().getOrDefault(EXTERNAL_USERNAME, "sinapse"),
                    System.getenv().getOrDefault(EXTERNAL_PASSWORD, "sinapse"));
        }
        PostgreSQLContainer<?> postgres =
                new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine")).withReuse(true);
        postgres.start();
        return new Database(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private record Database(String url, String username, String password) {
    }
}
