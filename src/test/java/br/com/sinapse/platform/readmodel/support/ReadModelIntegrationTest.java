package br.com.sinapse.platform.readmodel.support;

import br.com.sinapse.platform.curriculum.api.TopicView;
import br.com.sinapse.platform.educational.api.ClassroomView;
import br.com.sinapse.platform.educational.api.EducationalDirectory;
import br.com.sinapse.platform.educational.api.TeacherAccessPolicy;
import br.com.sinapse.platform.educational.internal.service.ClassroomService;
import br.com.sinapse.platform.educational.internal.service.InviteService;
import br.com.sinapse.platform.educational.internal.service.TeacherDirectory;
import br.com.sinapse.platform.identity.api.AccountDirectory;
import br.com.sinapse.platform.identity.api.ConsentPurpose;
import br.com.sinapse.platform.identity.internal.domain.Account;
import br.com.sinapse.platform.identity.internal.service.ConsentService;
import br.com.sinapse.platform.learningrecord.api.RecallRating;
import br.com.sinapse.platform.learningrecord.api.SessionKind;
import br.com.sinapse.platform.learningrecord.api.StudySessionView;
import br.com.sinapse.platform.learningrecord.internal.service.StudySessionService;
import br.com.sinapse.platform.planning.api.StudyPlanView;
import br.com.sinapse.platform.planning.orchestration.support.OrchestrationIntegrationTest;
import br.com.sinapse.platform.readmodel.internal.service.AgendaReadModel;
import br.com.sinapse.platform.readmodel.internal.service.ClassroomRosterReadModel;
import br.com.sinapse.platform.readmodel.internal.service.PlanSummaryReadModel;
import br.com.sinapse.platform.readmodel.internal.service.StudentPanelReadModel;
import br.com.sinapse.platform.readmodel.internal.service.StudentStateReadModel;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/**
 * Base of the read model tests.
 *
 * <p>Everything is built through the services that own it: an enrollment through the invite it
 * was redeemed from, a plan through the generation job that produced it, a study session through
 * the aggregate that opens and closes it. A read model asserted against rows a fixture wrote by
 * hand would prove that the composition works on data the platform cannot produce.
 *
 * <p>The one exception is a session in the past. A study session is append-only and closed by
 * trigger, and no clock a test can move takes {@code started_at} backwards through it — so the
 * rows a history is made of are aged with an update after the fact, and the rules the tests are
 * about are then exercised against them.
 */
@Import(ReadModelTestSupport.class)
public abstract class ReadModelIntegrationTest extends OrchestrationIntegrationTest {

    @Autowired
    protected StudentStateReadModel states;

    @Autowired
    protected AgendaReadModel agendas;

    @Autowired
    protected PlanSummaryReadModel summaries;

    @Autowired
    protected StudentPanelReadModel panels;

    @Autowired
    protected ClassroomRosterReadModel rosters;

    @Autowired
    protected QueryCounter queries;

    @Autowired
    protected StudySessionService studySessions;

    @Autowired
    protected ConsentService consents;

    @Autowired
    protected ClassroomService classrooms;

    @Autowired
    protected InviteService invites;

    @Autowired
    protected TeacherDirectory teacherRecords;

    @Autowired
    protected EducationalDirectory educational;

    @Autowired
    protected TeacherAccessPolicy teacherAccess;

    @Autowired
    protected AccountDirectory accountDirectory;

    /** An active account holding the teacher role, with the teacher record behind it. */
    protected Account teacherAccount() {
        Account account = student();
        jdbc.update("insert into account_role (account_id, role) values (?, 'TEACHER')",
                account.id());
        teacherRecords.record(account.id(), "Prof. Exemplo", "Universidade de Exemplo");
        return account;
    }

    /**
     * Puts a student into a classroom the way the platform does: an invite, redeemed.
     *
     * <p>The sharing consent is granted first because redemption requires it — the student
     * reads the preview, decides, and only then redeems, which is the order ADR 0005 makes the
     * whole arrangement depend on. That consent is also half of what makes the student readable
     * afterwards, so a fixture that inserted the enrollment directly would leave every teacher
     * read answering nothing and would prove the opposite of what it looked like.
     *
     * @param student   student joining
     * @param teacher   teacher who owns the classroom
     * @param classroom classroom
     */
    protected void enrol(Account student, Account teacher, ClassroomView classroom) {
        grantSharing(student);
        invites.redeem(student.id(), invites.issue(teacher.id(), classroom.id(), null, null).code());
    }

    /**
     * A classroom of a teacher.
     *
     * @param teacher owner
     * @return the classroom
     */
    protected ClassroomView classroomOf(Account teacher) {
        return classrooms.open(teacher.id(), "Turma de leitura", Set.of());
    }

    /**
     * Insert of a study session, bypassing the aggregate that opens and closes one.
     *
     * <p>Straight SQL, and this is the one place these tests need it. A closed session is
     * frozen by trigger — unconditionally, not merely during ordinary operation — and no clock
     * a test can set takes {@code started_at} backwards through it afterwards. Retroactive
     * entry is bounded to a few days by configuration, deliberately, so it cannot build three
     * months of history either. Inserting the row is not a way around a rule: inserts are the
     * one thing the triggers allow, and the rules the tests are about are then exercised
     * against the rows.
     */
    private static final String INSERT_SESSION = """
            insert into study_session (id, account_id, topic_id, planned_session_id, kind, source,
                                       status, started_at, ended_at, planned_duration_minutes,
                                       actual_duration_minutes, duration_source, recall_rating)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'MEASURED', ?)
            """;

    /**
     * A completed session on a topic, recorded as having happened in the past.
     *
     * @param student    who studied
     * @param topicId    what they studied
     * @param startedAgo how long before now the session started
     * @param minutes    how long it took
     * @param rating     what they judged
     * @return identifier of the session
     */
    protected UUID studied(Account student, UUID topicId, Duration startedAgo, int minutes,
            RecallRating rating) {

        return record(student, topicId, null, startedAgo, minutes, rating);
    }

    /**
     * A completed session that executes a planned one, recorded as having happened in the past.
     *
     * @param student          who studied
     * @param topicId          what they studied
     * @param plannedSessionId slot it executes
     * @param startedAgo       how long before now the session started
     * @return identifier of the session
     */
    protected UUID executed(Account student, UUID topicId, UUID plannedSessionId,
            Duration startedAgo) {

        return record(student, topicId, plannedSessionId, startedAgo, 45, RecallRating.GOOD);
    }

    /**
     * A session that was opened against a planned slot and abandoned.
     *
     * <p>Present because adherence turns on it: an abandoned session is not adherence, and a
     * measure that counted it would agree with itself rather than with what happened.
     *
     * @param student          who opened it
     * @param topicId          topic
     * @param plannedSessionId slot it was meant to execute
     * @param startedAgo       how long before now it started
     * @return identifier of the session
     */
    protected UUID abandoned(Account student, UUID topicId, UUID plannedSessionId,
            Duration startedAgo) {

        UUID id = UUID.randomUUID();
        Instant startedAt = clock.instant().minus(startedAgo);
        jdbc.update("""
                insert into study_session (id, account_id, topic_id, planned_session_id, kind,
                                           source, status, started_at, ended_at)
                values (?, ?, ?, ?, 'STUDY', ?, 'ABANDONED', ?, ?)
                """,
                id, student.id(), topicId, plannedSessionId,
                plannedSessionId == null ? "SELF_DIRECTED" : "FROM_PLAN",
                Timestamp.from(startedAt), Timestamp.from(startedAt.plus(Duration.ofMinutes(5))));
        return id;
    }

    /**
     * A session the student is still in the middle of, opened through the aggregate.
     *
     * <p>Through the service and not by insert: it is the partial index allowing one open
     * session per account that makes {@code openSessionId} meaningful, and a fixture that wrote
     * the row would be asserting against a state the platform refuses to reach.
     *
     * @param student who is studying
     * @param topicId what they are studying
     * @return the running session
     */
    protected StudySessionView leftOpen(Account student, UUID topicId) {
        return studySessions.start(student.id(), topicId, null, SessionKind.STUDY, 50);
    }

    private UUID record(Account student, UUID topicId, UUID plannedSessionId, Duration startedAgo,
            int minutes, RecallRating rating) {

        UUID id = UUID.randomUUID();
        Instant startedAt = clock.instant().minus(startedAgo);
        jdbc.update(INSERT_SESSION, id, student.id(), topicId, plannedSessionId, "STUDY",
                plannedSessionId == null ? "SELF_DIRECTED" : "FROM_PLAN", "COMPLETED",
                Timestamp.from(startedAt), Timestamp.from(startedAt.plus(Duration.ofMinutes(minutes))),
                plannedSessionId == null ? null : 50, minutes,
                rating == null ? null : rating.name());
        return id;
    }

    /** Plans a student and returns the plan in force. */
    protected StudyPlanView planFor(Account student) {
        generate(student);
        return directory.activePlanOf(student.id()).orElseThrow();
    }

    /** Withdraws the sharing consent, which is what cuts a teacher off immediately. */
    protected void revokeSharing(Account student) {
        consents.revoke(student.id(), ConsentPurpose.INSTITUTION_SHARING);
    }

    /** Grants the sharing consent again, which is what brings the teacher back. */
    protected void grantSharing(Account student) {
        consents.grant(student.id(), ConsentPurpose.INSTITUTION_SHARING,
                identity.currentTermsId(ConsentPurpose.INSTITUTION_SHARING), identity.evidence());
    }

    /** A window wide enough for every fixture here and inside the configured ceiling. */
    protected Instant windowStart() {
        return clock.instant().minus(Duration.ofDays(20));
    }

    /** The other end of {@link #windowStart()}. */
    protected Instant windowEnd() {
        return clock.instant().plus(Duration.ofDays(9));
    }

    /** Every topic the fixture gave a goal's subject. */
    protected List<TopicView> topicsOfGoal(Account student, int count) {
        return goalWithTopics(student, count);
    }

    /**
     * A plan with sessions on both sides of now, written straight to the database.
     *
     * <p>A generated plan is entirely in the future, and a planned session is immutable by
     * trigger, so there is no way through the aggregate to produce one that has already fallen
     * due. Adherence is about exactly those sessions, so the rows have to be written — and the
     * rule is then exercised against them.
     *
     * @param student student
     * @param topicId topic every session is about
     * @param overdue how many sessions ended before now
     * @param ahead   how many are still to come
     * @return the stored plan
     */
    protected StudyPlanView planWithDueSessions(Account student, UUID topicId, int overdue,
            int ahead) {

        UUID planId = UUID.randomUUID();
        jdbc.update(INSERT_PLAN, planId, student.id(), generationRequest(student),
                Date.valueOf(LocalDate.now(clock).minusDays(7)),
                Date.valueOf(LocalDate.now(clock).plusDays(21)),
                "ACTIVE", "{}", null, null);

        int index = 0;
        for (int session = 0; session < overdue; session++) {
            insertPlannedSession(planId, topicId,
                    clock.instant().minus(Duration.ofDays(2L + session)), index++);
        }
        for (int session = 0; session < ahead; session++) {
            insertPlannedSession(planId, topicId,
                    clock.instant().plus(Duration.ofDays(2L + session)), index++);
        }
        return directory.plan(planId).orElseThrow();
    }

    private void insertPlannedSession(UUID planId, UUID topicId, Instant start, int index) {
        jdbc.update(INSERT_PLANNED_SESSION, UUID.randomUUID(), planId, topicId, "STUDY",
                Timestamp.from(start), 50, index);
    }
}
