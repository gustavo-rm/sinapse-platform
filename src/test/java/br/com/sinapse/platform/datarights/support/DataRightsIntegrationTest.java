package br.com.sinapse.platform.datarights.support;

import br.com.sinapse.platform.curriculum.api.TopicView;
import br.com.sinapse.platform.datarights.internal.job.ErasureJob;
import br.com.sinapse.platform.datarights.internal.service.ErasureRequestService;
import br.com.sinapse.platform.datarights.internal.service.ErasureService;
import br.com.sinapse.platform.educational.api.ClassroomView;
import br.com.sinapse.platform.educational.internal.service.ClassroomService;
import br.com.sinapse.platform.educational.internal.service.InviteService;
import br.com.sinapse.platform.educational.internal.service.TeacherDirectory;
import br.com.sinapse.platform.identity.api.ConsentPurpose;
import br.com.sinapse.platform.identity.internal.domain.Account;
import br.com.sinapse.platform.identity.internal.service.ConsentService;
import br.com.sinapse.platform.learningrecord.api.RecallRating;
import br.com.sinapse.platform.learningrecord.api.SessionKind;
import br.com.sinapse.platform.learningrecord.api.StudySessionView;
import br.com.sinapse.platform.learningrecord.internal.service.StudySessionService;
import br.com.sinapse.platform.planning.orchestration.support.OrchestrationIntegrationTest;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/**
 * Base of the data subject rights tests.
 *
 * <p>An erasure is only interesting against an account that has something in every module, so
 * the fixture builds one the way the platform would: consents, a session, a token, a membership
 * of a real classroom, study sessions, availability, goals, and a plan generated through the
 * job — which is what puts a snapshot on a generation request, the single most sensitive
 * artifact in the system.
 */
@Import(DataRightsTestSupport.class)
public abstract class DataRightsIntegrationTest extends OrchestrationIntegrationTest {

    @Autowired
    protected ErasureRequestService erasureRequests;

    @Autowired
    protected ErasureService erasureService;

    @Autowired
    protected ErasureJob erasureJob;

    @Autowired
    protected ControllableModuleDataRights probe;

    @Autowired
    protected StudySessionService studySessions;

    @Autowired
    protected ConsentService consents;

    @Autowired
    protected InviteService invites;

    @Autowired
    protected ClassroomService classrooms;

    @Autowired
    protected TeacherDirectory teachers;

    @Autowired
    protected br.com.sinapse.platform.educational.api.EducationalDirectory educational;

    @BeforeEach
    void resetDataRightsState() {
        probe.setFailing(false);
    }

    /**
     * A student with something in every module that holds personal data.
     *
     * @return the account
     */
    protected Account fullyPopulatedStudent() {
        Account student = studentReadyToPlan(3);
        consents.grant(student.id(), ConsentPurpose.INSTITUTION_SHARING,
                identity.currentTermsId(ConsentPurpose.INSTITUTION_SHARING), identity.evidence());

        identity.insertGuardian(student.id(), true);
        insertToken(student.id());
        tokenFor(student);

        joinAClassroom(student);
        studySomething(student);
        generate(student);
        return student;
    }

    /** Enrols the student in a classroom of a real teacher, which is what grants a teacher access. */
    protected ClassroomView joinAClassroom(Account student) {
        Account teacher = teacherAccount();
        ClassroomView classroom = classrooms.open(teacher.id(), "Turma de eliminação", Set.of());
        invites.redeem(student.id(), invites.issue(teacher.id(), classroom.id(), null, null).code());
        return classroom;
    }

    /** An active account holding the teacher role, with the teacher record behind it. */
    protected Account teacherAccount() {
        Account account = student();
        jdbc.update("insert into account_role (account_id, role) values (?, 'TEACHER')",
                account.id());
        teachers.record(account.id(), "Prof. Exemplo", "Universidade de Exemplo");
        return account;
    }

    /** Two completed study sessions, so the history is not empty. */
    protected void studySomething(Account student) {
        for (TopicView topic : goalWithTopics(student, 2)) {
            StudySessionView started = studySessions.start(student.id(), topic.id(),
                    UUID.randomUUID(), SessionKind.STUDY, 50);
            studySessions.complete(student.id(), started.id(), RecallRating.GOOD, 45);
        }
    }

    /** A verification token, which nothing in a normal flow leaves lying around. */
    protected UUID insertToken(UUID accountId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into account_token (id, account_id, purpose, token_hash, expires_at)
                values (?, ?, 'PASSWORD_RESET', ?, ?)
                """, id, accountId, "hash-" + id,
                Timestamp.from(clock.instant().plus(Duration.ofHours(1))));
        return id;
    }

    /** How many rows of a table belong to an account. */
    protected int countFor(String table, UUID accountId) {
        return jdbc.queryForObject("select count(*) from " + table + " where account_id = ?",
                Integer.class, accountId);
    }

    /** How many planned sessions belong to an account's plans. */
    protected int plannedSessionCountFor(UUID accountId) {
        return jdbc.queryForObject("""
                select count(*) from planned_session
                 where plan_id in (select id from study_plan where account_id = ?)
                """, Integer.class, accountId);
    }

    /** What the database holds for one column of one account. */
    protected <T> T accountColumn(UUID accountId, String column, Class<T> type) {
        return jdbc.queryForObject("select " + column + " from account where id = ?", type,
                accountId);
    }

    /** Requests erasure and runs the sweep, ignoring the window. */
    protected UUID eraseNow(Account student) {
        UUID requestId = erasureRequests.request(student.id()).id();
        // Both instants move: the check constraint requires the effective one to be strictly
        // after the request, and the sweep only takes requests whose window has elapsed.
        jdbc.update("update erasure_request set requested_at = now() - interval '2 seconds', "
                + "effective_at = now() - interval '1 second' where id = ?", requestId);
        erasureJob.runOnce();
        return requestId;
    }

    /** The status of an erasure request, read straight from the table. */
    protected String erasureStatusOf(UUID requestId) {
        return jdbc.queryForObject("select status from erasure_request where id = ?", String.class,
                requestId);
    }

    /** Every table listed in ADR 0011 as not surviving, with the account column they hang from. */
    protected static List<String> erasedTables() {
        return List.of("guardian", "user_session", "account_token", "study_session",
                "study_availability", "study_goal", "study_plan", "plan_generation_request");
    }
}
