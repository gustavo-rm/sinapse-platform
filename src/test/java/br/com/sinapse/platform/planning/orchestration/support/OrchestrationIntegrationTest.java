package br.com.sinapse.platform.planning.orchestration.support;

import br.com.sinapse.platform.curriculum.api.SubjectView;
import br.com.sinapse.platform.curriculum.api.TopicView;
import br.com.sinapse.platform.identity.internal.domain.Account;
import br.com.sinapse.platform.planning.api.GenerationRequestView;
import br.com.sinapse.platform.planning.internal.service.GenerationRequestService;
import br.com.sinapse.platform.planning.orchestration.PlanGenerationWorker;
import br.com.sinapse.platform.planning.support.PlanningIntegrationTest;
import br.com.sinapse.platform.shared.ratelimit.InMemoryRateLimiter;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/**
 * Base of the generation tests.
 *
 * <p>Everything the planning tests have, plus the two substitutions of
 * {@link OrchestrationTestSupport} and a student who is actually ready to be planned for —
 * availability every weekday and a goal with topics behind it, which is what decision F2
 * requires before a plan may be asked for at all.
 */
@Import(OrchestrationTestSupport.class)
public abstract class OrchestrationIntegrationTest extends PlanningIntegrationTest {

    @Autowired
    protected GenerationRequestService requestService;

    @Autowired
    protected PlanGenerationWorker worker;

    @Autowired
    protected StubSinapseCore core;

    @Autowired
    protected FixedSeedSource seeds;

    @Autowired
    private InMemoryRateLimiter rateLimiter;

    @BeforeEach
    void resetOrchestrationState() {
        core.reset();
        seeds.setSeed(20260906L);
        rateLimiter.reset();
    }

    /**
     * A student who can be planned for: a weekday evening routine and one goal.
     *
     * @param topicCount how many topics the goal's subject has
     * @return the account
     */
    protected Account studentReadyToPlan(int topicCount) {
        Account student = student();
        declareWeekdayEvenings(student);
        goalWithTopics(student, topicCount);
        return student;
    }

    /**
     * Declares Monday to Friday, seven to nine in the evening, from today onwards.
     *
     * @param student student
     */
    protected void declareWeekdayEvenings(Account student) {
        LocalDate from = LocalDate.now(clock);
        for (DayOfWeek day : List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)) {
            availability.declare(student.id(), day, LocalTime.of(19, 0), LocalTime.of(21, 0),
                    from, null);
        }
    }

    /**
     * Sets a goal on a fresh subject and fills that subject with topics.
     *
     * @param student    student
     * @param topicCount how many topics the subject has
     * @return the topics, in curricular order
     */
    protected List<TopicView> goalWithTopics(Account student, int topicCount) {
        SubjectView subject = subject();
        List<TopicView> topics = new ArrayList<>();
        for (int index = 0; index < topicCount; index++) {
            topics.add(topicOf(subject));
        }
        goals.set(student.id(), subject.id(), null, 3);
        return topics;
    }

    /**
     * Queues a job and runs the worker once.
     *
     * @param student student
     * @return the job, as it stands after the pass
     */
    protected GenerationRequestView generate(Account student) {
        GenerationRequestView queued = requestService.queue(student.id(), null);
        worker.runOnce();
        return requestService.require(student.id(), queued.id());
    }

    /** What the database holds for one column of one generation job. */
    protected <T> T requestColumn(UUID requestId, String column, Class<T> type) {
        return jdbc.queryForObject(
                "select " + column + " from plan_generation_request where id = ?", type, requestId);
    }
}
