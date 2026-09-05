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

    /**
     * Every table the migrations create, and the history table Flyway creates for itself.
     *
     * <p>The list is written out rather than derived, because deriving it from the same
     * migrations it is meant to check would assert nothing. It grows with each module.
     */
    private static final List<String> EXPECTED_TABLES = List.of(
            "account", "account_role", "account_token", "catalog_import", "classroom",
            "classroom_subject", "consent_record", "enrollment", "erasure_request",
            "flyway_schema_history", "guardian", "invite", "plan_generation_request",
            "planned_session", "study_availability", "study_goal", "study_plan",
            "study_session", "subject", "teacher", "terms_version", "topic",
            "topic_prerequisite", "user_session");

    @Test
    void schemaContainsOnlyWhatFlywayPut() {
        List<String> tables = jdbcTemplate.queryForList("""
                select table_name from information_schema.tables
                where table_schema = 'public'
                order by table_name
                """, String.class);

        assertThat(tables)
                .as("the schema belongs to Flyway. A table here that no migration describes is a "
                        + "table Hibernate created, which ddl-auto: validate exists to prevent")
                .containsExactlyElementsOf(EXPECTED_TABLES);
    }
}
