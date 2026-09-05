package br.com.sinapse.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Confirms that the application context starts against a real PostgreSQL and that
 * Flyway, not Hibernate, owns the schema.
 */
class ApplicationContextIntegrationTest extends IntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void contextStartsAgainstPostgres() {
        assertThat(dataSource).isNotNull();
        assertThat(jdbcTemplate.queryForObject("select version()", String.class))
                .contains("PostgreSQL");
    }

    @Test
    void schemaContainsOnlyWhatFlywayPut() {
        List<String> tables = jdbcTemplate.queryForList("""
                select table_name from information_schema.tables
                where table_schema = 'public'
                order by table_name
                """, String.class);

        assertThat(tables)
                .as("the bootstrap creates no domain table: only Flyway's own history and "
                        + "the event publication registry required by Spring Modulith")
                .containsExactly("event_publication", "flyway_schema_history");
    }
}
