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
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTest {

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withReuse(true);

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
