package br.com.sinapse.platform.planning.support;

import br.com.sinapse.platform.IntegrationTest;
import br.com.sinapse.platform.curriculum.api.CatalogCuration;
import br.com.sinapse.platform.curriculum.api.EffortTier;
import br.com.sinapse.platform.curriculum.api.SubjectView;
import br.com.sinapse.platform.curriculum.api.TopicView;
import br.com.sinapse.platform.identity.internal.domain.Account;
import br.com.sinapse.platform.identity.internal.service.SessionService;
import br.com.sinapse.platform.identity.support.IdentityFixtures;
import br.com.sinapse.platform.identity.support.IdentityTestSupport;
import br.com.sinapse.platform.planning.api.PlannedSessionKind;
import br.com.sinapse.platform.planning.api.PlanningDirectory;
import br.com.sinapse.platform.planning.api.StudyPlanView;
import br.com.sinapse.platform.planning.internal.persistence.PlanGenerationRequestRepository;
import br.com.sinapse.platform.planning.internal.service.AvailabilityService;
import br.com.sinapse.platform.planning.internal.service.GoalService;
import br.com.sinapse.platform.planning.internal.service.StudyPlanService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Base of the planning integration tests.
 *
 * <p>Accounts and subjects are built through the services that own them rather than inserted:
 * a goal refused because a subject does not exist proves nothing if the subject was faked, and
 * a use case refused because an account may not be processed proves nothing if the account
 * state was written by hand.
 *
 * <p>Rows written straight through SQL appear only where the database itself is what is under
 * test. The inserts used for that are here, because more than one test needs a plan in a state
 * the application refuses to produce.
 */
@Import(IdentityTestSupport.class)
public abstract class PlanningIntegrationTest extends IntegrationTest {

    /** Insert that bypasses the aggregate, for the tests whose subject is the database. */
    protected static final String INSERT_PLAN = """
            insert into study_plan (id, account_id, generation_request_id, horizon_start,
                                    horizon_end, status, fitness, superseded_at,
                                    superseded_by_plan_id)
            values (?, ?, ?, ?, ?, ?, cast(? as jsonb), ?, ?)
            """;

    /** Insert of a planned session, bypassing the plan that owns it. */
    protected static final String INSERT_PLANNED_SESSION = """
            insert into planned_session (id, plan_id, topic_id, kind, scheduled_start,
                                         duration_minutes, sequence_index)
            values (?, ?, ?, ?, ?, ?, ?)
            """;

    /** Insert of a generation job, bypassing a consumer that does not exist yet. */
    protected static final String INSERT_REQUEST = """
            insert into plan_generation_request (id, account_id, status, horizon_start,
                                                 horizon_end)
            values (?, ?, ?, ?, ?)
            """;

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected IdentityFixtures identity;

    @Autowired
    protected CatalogCuration curation;

    @Autowired
    protected AvailabilityService availability;

    @Autowired
    protected GoalService goals;

    @Autowired
    protected StudyPlanService plans;

    @Autowired
    protected PlanningDirectory directory;

    @Autowired
    protected PlanGenerationRequestRepository requests;

    @Autowired
    protected SessionService userSessions;

    @Autowired
    protected Clock clock;

    @BeforeEach
    void resetPlanningState() {
        // Cascades from both roots: accounts reach availability, goals, jobs and plans;
        // subjects reach topics and through them the planned sessions that name one. The
        // published consent terms are seeded once per context and survive, which is what lets
        // an account be activated.
        jdbc.execute("truncate table account, subject cascade");
    }

    /** An active account whose learning data may be processed. */
    protected Account student() {
        return identity.activeAdult(identity.uniqueEmail());
    }

    /** A subject in the curriculum catalogue. */
    protected SubjectView subject() {
        return curation.defineSubject("SUB-" + SEQUENCE.incrementAndGet(), "Disciplina");
    }

    /** A topic of a fresh subject, which is all a planned session needs from the catalogue. */
    protected TopicView topic() {
        return topicOf(subject());
    }

    /** Another topic of an existing subject. */
    protected TopicView topicOf(SubjectView subject) {
        int position = SEQUENCE.incrementAndGet();
        return curation.defineTopic(new CatalogCuration.TopicDefinition(subject.id(),
                "TOP-" + position, "Tópico " + position, position, EffortTier.STANDARD));
    }

    /**
     * A finished generation job, which a plan needs behind it.
     *
     * <p>Written directly, and in a terminal state. The consumer that moves a job from
     * {@code PENDING} to {@code READY} is the next step of the build, so there is nothing to
     * call; and the partial index allows one unfinished job per account, so a fixture that
     * left them pending could not produce two plans for one student — which is the whole of
     * re-planning.
     *
     * @param account student
     * @return identifier of the job
     */
    protected UUID generationRequest(Account account) {
        UUID id = UUID.randomUUID();
        LocalDate start = LocalDate.now(clock);
        jdbc.update(INSERT_REQUEST, id, account.id(), "READY", start, start.plusWeeks(4));
        return id;
    }

    /**
     * A stored plan with the given number of sessions on one topic, an hour apart.
     *
     * @param account  student
     * @param topicId  topic every session is about
     * @param sessions how many sessions to schedule
     * @return the stored plan
     */
    protected StudyPlanView plan(Account account, UUID topicId, int sessions) {
        LocalDate start = LocalDate.now(clock);
        Instant first = clock.instant().plus(Duration.ofHours(1));
        List<StudyPlanService.ScheduledSession> scheduled = java.util.stream.IntStream
                .range(0, sessions)
                .mapToObj(index -> new StudyPlanService.ScheduledSession(topicId,
                        PlannedSessionKind.STUDY, first.plus(Duration.ofHours(index)), 50, index))
                .toList();

        return plans.store(account.id(), generationRequest(account), start, start.plusWeeks(4),
                Map.of("coverage", 0.8), scheduled);
    }

    /** An opaque session token for an account. */
    protected String tokenFor(Account account) {
        return userSessions.open(account, "203.0.113.7", "integration-test").token();
    }

    /** What the database holds for one column of one plan. */
    protected <T> T planColumn(UUID planId, String column, Class<T> type) {
        return jdbc.queryForObject("select " + column + " from study_plan where id = ?", type,
                planId);
    }

    /** How many plans the account has on record. */
    protected int planCount(UUID accountId) {
        return jdbc.queryForObject("select count(*) from study_plan where account_id = ?",
                Integer.class, accountId);
    }

    /** How many planned sessions the plan has. */
    protected int plannedSessionCount(UUID planId) {
        return jdbc.queryForObject("select count(*) from planned_session where plan_id = ?",
                Integer.class, planId);
    }
}
