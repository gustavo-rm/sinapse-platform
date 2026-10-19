package br.com.sinapse.platform.planning.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.sinapse.platform.coreclient.api.CoreProtocolException;
import br.com.sinapse.platform.coreclient.api.CoreUnavailableException;
import br.com.sinapse.platform.coreclient.api.SinapseCore;
import br.com.sinapse.platform.identity.internal.domain.Account;
import br.com.sinapse.platform.planning.api.GenerationRequestStatus;
import br.com.sinapse.platform.planning.api.GenerationRequestView;
import br.com.sinapse.platform.planning.internal.service.GenerationRequestService.ClaimedJob;
import br.com.sinapse.platform.planning.orchestration.CoreCallTimingTest.SteppingClock;
import br.com.sinapse.platform.planning.orchestration.support.OrchestrationIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code core_call_millis}: the latency of the call to the core, as the platform measured it.
 *
 * <p>Additive and nullable: the jobs that finished before it existed, and every job that has not
 * succeeded, hold {@code NULL}, never 0. The value is the difference the orchestrator's clock
 * moved across the call. It is an operator's number, so it appears in no student route and in
 * no published schema. {@code elapsed_millis} keeps holding what the core reported.
 */
class CoreCallMillisIntegrationTest extends OrchestrationIntegrationTest {

    private static final String MIGRATION_SCHEMA = "core_call_millis_migration_check";

    /** Every spelling the field could take on the way out. */
    private static final List<String> SPELLINGS =
            List.of("coreCallMillis", "core_call_millis", "callMillis", "core-call-millis");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SnapshotAssembler assembler;

    @Autowired
    private GeneratedPlanWriter writer;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DataSource dataSource;

    @Test
    void theRecordedValueIsTheDifferenceOfTheInjectedClock() {
        Account student = studentReadyToPlan(3);
        UUID jobId = requestService.queue(student.id(), null).id();
        ClaimedJob job = requestService.claimNext().get(0);
        assertThat(job.id()).isEqualTo(jobId);

        SteppingClock clock = new SteppingClock(Instant.parse("2026-10-19T12:00:00Z"));
        SinapseCore timed = request -> {
            clock.advance(Duration.ofMillis(4_321));
            return core.generate(request);
        };
        new PlanGenerationOrchestrator(assembler, timed, requestService, writer, seeds,
                objectMapper, clock).run(job);

        assertThat(requestColumn(jobId, "status", String.class)).isEqualTo("READY");
        assertThat(requestColumn(jobId, "core_call_millis", Long.class)).isEqualTo(4_321L);
        assertThat(requestColumn(jobId, "elapsed_millis", Long.class))
                .as("still what the core reported, untouched by the measurement")
                .isZero();
    }

    @Test
    void aSucceededJobRecordsALatencyThatIsNeverNegative() {
        GenerationRequestView job = generate(studentReadyToPlan(3));

        assertThat(job.status()).isEqualTo(GenerationRequestStatus.READY);
        assertThat(requestColumn(job.id(), "core_call_millis", Long.class))
                .isNotNull()
                .isNotNegative();
    }

    @Test
    void aFailedCallRecordsNothing() {
        core.alwaysFail(() -> new CoreProtocolException("the core rejected the request"));

        GenerationRequestView job = generate(studentReadyToPlan(3));

        assertThat(job.status()).isEqualTo(GenerationRequestStatus.FAILED);
        assertThat(requestColumn(job.id(), "core_call_millis", Long.class)).isNull();
        assertThat(requestColumn(job.id(), "elapsed_millis", Long.class)).isNull();
    }

    @Test
    void aRetriedJobRecordsTheAttemptThatProducedThePlanOnly() {
        core.failNext(1, () -> new CoreUnavailableException("the core did not answer"));
        Account student = studentReadyToPlan(3);

        GenerationRequestView first = generate(student);
        assertThat(first.status()).isEqualTo(GenerationRequestStatus.PENDING);
        assertThat(requestColumn(first.id(), "core_call_millis", Long.class))
                .as("the failed attempt left nothing behind")
                .isNull();

        worker.runOnce();
        assertThat(requestService.require(student.id(), first.id()).status())
                .isEqualTo(GenerationRequestStatus.READY);
        assertThat(requestColumn(first.id(), "core_call_millis", Long.class)).isNotNull();
    }

    @Test
    void noStudentRouteCarriesTheLatency() throws Exception {
        Account student = studentReadyToPlan(3);
        GenerationRequestView job = generate(student);
        assertThat(job.status()).isEqualTo(GenerationRequestStatus.READY);
        assertThat(requestColumn(job.id(), "core_call_millis", Long.class)).isNotNull();
        String token = tokenFor(student);

        for (String route : List.of(
                "/api/v1/study-plans/generation-requests/" + job.id(),
                "/api/v1/study-plans/current",
                "/api/v1/study-plans",
                "/api/v1/study-plans/" + job.planId(),
                "/api/v1/study-plans/" + job.planId() + "/sessions",
                "/api/v1/study-plans/" + job.planId() + "/summary",
                "/api/v1/me/state",
                "/api/v1/me/data-export")) {
            String body = mockMvc.perform(get(route)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            for (String spelling : SPELLINGS) {
                assertThat(body).as("%s carries %s", route, spelling)
                        .doesNotContainIgnoringCase(spelling);
            }
        }
    }

    @Test
    void thePublishedSchemaDoesNotNameTheLatency() throws Exception {
        String schema = mockMvc.perform(get("/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        for (String spelling : SPELLINGS) {
            assertThat(schema).doesNotContainIgnoringCase(spelling);
        }
    }

    /**
     * The migration over a database that already holds finished jobs: they keep every value
     * they had and get {@code NULL}, not 0, for a latency nobody measured.
     *
     * <p>Run in a schema of its own, migrated to the version before this one, filled, and then
     * migrated to the end, so that the rows exist before the column does.
     */
    @Test
    void theMigrationLeavesExistingJobsNullAndTouchesNothingElse() {
        JdbcTemplate sql = new JdbcTemplate(dataSource);
        sql.execute("drop schema if exists " + MIGRATION_SCHEMA + " cascade");
        try {
            migrate("8");
            assertThat(sql.queryForObject("select count(*) from information_schema.columns "
                    + "where table_schema = ? and table_name = 'plan_generation_request' "
                    + "and column_name = 'core_call_millis'", Integer.class, MIGRATION_SCHEMA))
                    .as("the row below is written before the column exists")
                    .isZero();
            UUID accountId = UUID.randomUUID();
            UUID jobId = UUID.randomUUID();
            sql.update("insert into " + MIGRATION_SCHEMA + ".account (id, email, password_hash, "
                    + "date_of_birth, time_zone, status) values (?, ?, 'not-a-real-hash', "
                    + "date '1990-01-01', 'America/Sao_Paulo', 'ACTIVE')",
                    accountId, identity.uniqueEmail());
            sql.update("insert into " + MIGRATION_SCHEMA + ".plan_generation_request (id, "
                    + "account_id, status, horizon_start, horizon_end, core_version, "
                    + "algorithm_params, random_seed, generations, elapsed_millis, attempt_count) "
                    + "values (?, ?, 'READY', date '2026-10-01', date '2026-10-29', 'core-2.0.1', "
                    + "cast(? as jsonb), 7, 60, 0, 1)",
                    jobId, accountId, "{\"generations\":400}");

            migrate(null);

            Map<String, Object> row = sql.queryForMap("select core_call_millis, elapsed_millis, "
                    + "generations, core_version, algorithm_params::text as params from "
                    + MIGRATION_SCHEMA + ".plan_generation_request where id = ?", jobId);
            assertThat(row.get("core_call_millis")).as("never measured, so NULL and not 0")
                    .isNull();
            assertThat(row.get("elapsed_millis")).isEqualTo(0L);
            assertThat(row.get("generations")).isEqualTo(60);
            assertThat(row.get("core_version")).isEqualTo("core-2.0.1");
            assertThat(row.get("params")).isEqualTo("{\"generations\": 400}");
            assertThat(sql.queryForObject("select column_default from information_schema.columns "
                    + "where table_schema = ? and table_name = 'plan_generation_request' "
                    + "and column_name = 'core_call_millis'", String.class, MIGRATION_SCHEMA))
                    .as("no default, so a row written without it says nothing rather than 0")
                    .isNull();
            assertThat(sql.queryForObject("select is_nullable from information_schema.columns "
                    + "where table_schema = ? and table_name = 'plan_generation_request' "
                    + "and column_name = 'core_call_millis'", String.class, MIGRATION_SCHEMA))
                    .isEqualTo("YES");
        } finally {
            sql.execute("drop schema if exists " + MIGRATION_SCHEMA + " cascade");
        }
    }

    private void migrate(String target) {
        var configuration = Flyway.configure()
                .dataSource(dataSource)
                .schemas(MIGRATION_SCHEMA)
                .locations("classpath:db/migration");
        if (target != null) {
            configuration.target(target);
        }
        configuration.load().migrate();
    }
}
