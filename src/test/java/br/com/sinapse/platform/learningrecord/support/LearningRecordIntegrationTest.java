package br.com.sinapse.platform.learningrecord.support;

import br.com.sinapse.platform.IntegrationTest;
import br.com.sinapse.platform.curriculum.api.CatalogCuration;
import br.com.sinapse.platform.curriculum.api.EffortTier;
import br.com.sinapse.platform.curriculum.api.SubjectView;
import br.com.sinapse.platform.curriculum.api.TopicView;
import br.com.sinapse.platform.identity.internal.domain.Account;
import br.com.sinapse.platform.identity.internal.service.SessionService;
import br.com.sinapse.platform.identity.support.IdentityFixtures;
import br.com.sinapse.platform.identity.support.IdentityTestSupport;
import br.com.sinapse.platform.learningrecord.api.RecallRating;
import br.com.sinapse.platform.learningrecord.api.SessionKind;
import br.com.sinapse.platform.learningrecord.api.StudyHistory;
import br.com.sinapse.platform.learningrecord.internal.service.StudySessionService;
import br.com.sinapse.platform.shared.ratelimit.InMemoryRateLimiter;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Base of the learning record integration tests.
 *
 * <p>Accounts and topics are built through the services that own them rather than inserted:
 * a session refused because the account may not be processed proves nothing if the account
 * state was faked, and a session accepted against a topic proves nothing if the topic was not
 * one the catalogue would have produced.
 *
 * <p>Rows written straight through SQL appear only where the database itself is what is under
 * test. The insert used for that is here, because more than one test needs a session in a
 * state the application refuses to produce.
 */
@Import(IdentityTestSupport.class)
public abstract class LearningRecordIntegrationTest extends IntegrationTest {

    /** Insert that bypasses the aggregate, for the tests whose subject is the database. */
    protected static final String INSERT_SESSION = """
            insert into study_session (id, account_id, topic_id, planned_session_id, kind, source,
                                       status, started_at, ended_at, planned_duration_minutes,
                                       actual_duration_minutes, duration_source, recall_rating)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected IdentityFixtures identity;

    @Autowired
    protected CatalogCuration curation;

    @Autowired
    protected StudySessionService sessions;

    @Autowired
    protected StudyHistory history;

    @Autowired
    protected SessionService userSessions;

    @Autowired
    protected Clock clock;

    @Autowired
    private InMemoryRateLimiter rateLimiter;

    @BeforeEach
    void resetLearningRecordState() {
        // Cascades from both roots: accounts reach study sessions, subjects reach topics and
        // through them the sessions that name one. The published consent terms are seeded once
        // per context and survive, which is what lets an account be activated.
        jdbc.execute("truncate table account, subject cascade");
        rateLimiter.reset();
    }

    /** An active account whose learning data may be processed. */
    protected Account student() {
        return identity.activeAdult(identity.uniqueEmail());
    }

    /** A topic of a fresh subject, which is all a session needs from the catalogue. */
    protected TopicView topic() {
        SubjectView subject = curation.defineSubject("SUB-" + SEQUENCE.incrementAndGet(),
                "Disciplina");
        return topicOf(subject);
    }

    /** Another topic of an existing subject. */
    protected TopicView topicOf(SubjectView subject) {
        int position = SEQUENCE.incrementAndGet();
        return curation.defineTopic(new CatalogCuration.TopicDefinition(subject.id(),
                "TOP-" + position, "Tópico " + position, position, EffortTier.STANDARD));
    }

    /** A subject to hang several topics off. */
    protected SubjectView subject() {
        return curation.defineSubject("SUB-" + SEQUENCE.incrementAndGet(), "Disciplina");
    }

    /**
     * A session recorded after the fact, which is the only way a test can choose a duration.
     *
     * <p>A timed session lasts however long the two calls take, which is zero minutes.
     *
     * @param account    student
     * @param topicId    topic studied
     * @param minutesAgo how long ago it started
     * @param minutes    how long it lasted
     * @param rating     recall rating
     * @return the identifier of the recorded session
     */
    protected UUID recordedSession(Account account, UUID topicId, long minutesAgo, int minutes,
            RecallRating rating) {

        Instant startedAt = clock.instant().minusSeconds(minutesAgo * 60);
        return sessions.recordRetroactively(account.id(), topicId, null, SessionKind.STUDY,
                startedAt, minutes, rating).id();
    }

    /** An opaque session token for an account. */
    protected String tokenFor(Account account) {
        return userSessions.open(account, "203.0.113.7", "integration-test").token();
    }

    /** What the database holds for one column of one session. */
    protected <T> T columnOf(UUID sessionId, String column, Class<T> type) {
        return jdbc.queryForObject("select " + column + " from study_session where id = ?", type,
                sessionId);
    }

    /** How many sessions the account has on record. */
    protected int sessionCount(UUID accountId) {
        return jdbc.queryForObject("select count(*) from study_session where account_id = ?",
                Integer.class, accountId);
    }
}
