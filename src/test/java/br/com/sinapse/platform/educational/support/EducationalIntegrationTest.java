package br.com.sinapse.platform.educational.support;

import br.com.sinapse.platform.IntegrationTest;
import br.com.sinapse.platform.curriculum.api.CatalogCuration;
import br.com.sinapse.platform.curriculum.api.SubjectView;
import br.com.sinapse.platform.educational.api.ClassroomView;
import br.com.sinapse.platform.educational.internal.domain.Invite;
import br.com.sinapse.platform.educational.internal.service.ClassroomService;
import br.com.sinapse.platform.educational.internal.service.InviteService;
import br.com.sinapse.platform.educational.internal.service.TeacherDirectory;
import br.com.sinapse.platform.identity.api.ConsentPurpose;
import br.com.sinapse.platform.identity.internal.domain.Account;
import br.com.sinapse.platform.identity.internal.service.ConsentService;
import br.com.sinapse.platform.identity.internal.service.SessionService;
import br.com.sinapse.platform.identity.support.IdentityFixtures;
import br.com.sinapse.platform.identity.support.IdentityTestSupport;
import br.com.sinapse.platform.shared.ratelimit.InMemoryRateLimiter;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Base of the educational integration tests.
 *
 * <p>This module is the first that cannot be tested on its own. Every interesting rule it has
 * involves an account that is active and has consented, so the fixtures build real identity
 * accounts through identity's own services rather than inserting rows that look like them —
 * a redemption refused because a consent is missing proves nothing if the consent was faked.
 *
 * <p>The teacher role is the exception, and it is inserted directly. How an account comes to
 * hold it is unresolved (P2) and no code path in the platform grants it, so there is nothing
 * to call.
 */
@Import(IdentityTestSupport.class)
public abstract class EducationalIntegrationTest extends IntegrationTest {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected IdentityFixtures identity;

    @Autowired
    protected ConsentService consents;

    @Autowired
    protected SessionService sessions;

    @Autowired
    protected TeacherDirectory teachers;

    @Autowired
    protected ClassroomService classrooms;

    @Autowired
    protected InviteService invites;

    @Autowired
    protected CatalogCuration curation;

    @Autowired
    private InMemoryRateLimiter rateLimiter;

    @BeforeEach
    void resetEducationalState() {
        // Cascades from both roots: accounts reach teachers, classrooms, invites and
        // enrollments; subjects reach the classroom-subject links. The published consent terms
        // are seeded once per context and survive, which is what lets a student consent.
        jdbc.execute("truncate table account, subject cascade");
        rateLimiter.reset();
    }

    /** An active account that has consented to sharing its data with an institution. */
    protected Account student() {
        Account account = identity.activeAdult(identity.uniqueEmail());
        consents.grant(account.id(), ConsentPurpose.INSTITUTION_SHARING,
                identity.currentTermsId(ConsentPurpose.INSTITUTION_SHARING), identity.evidence());
        return account;
    }

    /** An active account that has not consented to sharing. */
    protected Account studentWithoutSharingConsent() {
        return identity.activeAdult(identity.uniqueEmail());
    }

    /**
     * An active account holding the teacher role, with the teacher record behind it.
     *
     * @param displayName name a student will see on an invite preview
     * @return the account
     */
    protected Account teacher(String displayName) {
        Account account = identity.activeAdult(identity.uniqueEmail());
        grantTeacherRole(account.id());
        teachers.record(account.id(), displayName, "Universidade de Exemplo");
        return account;
    }

    /**
     * Grants the teacher role by writing the row.
     *
     * <p>P2 is unresolved and nothing in the platform grants this role, so there is no service
     * to call. The recommendation on record is a manual, audited administrative operation.
     *
     * @param accountId account to grant it to
     */
    protected void grantTeacherRole(UUID accountId) {
        jdbc.update("insert into account_role (account_id, role) values (?, 'TEACHER')", accountId);
    }

    /** A classroom owned by the given teacher, covering no subjects. */
    protected ClassroomView classroom(Account teacher) {
        return classrooms.open(teacher.id(), "Turma " + SEQUENCE.incrementAndGet(), Set.of());
    }

    /** A classroom owned by the given teacher, covering the given subjects. */
    protected ClassroomView classroom(Account teacher, Set<UUID> subjectIds) {
        return classrooms.open(teacher.id(), "Turma " + SEQUENCE.incrementAndGet(), subjectIds);
    }

    /** A subject in the curriculum catalogue, for the preview to name. */
    protected SubjectView subject(String name) {
        return curation.defineSubject("SUB-" + SEQUENCE.incrementAndGet(), name);
    }

    /** An invite for a classroom, with the default lifetime and no use limit. */
    protected Invite invite(Account teacher, ClassroomView classroom) {
        return invites.issue(teacher.id(), classroom.id(), null, null);
    }

    /** An invite with a chosen lifetime and use limit. */
    protected Invite invite(Account teacher, ClassroomView classroom, Duration lifetime,
            Integer maxUses) {
        return invites.issue(teacher.id(), classroom.id(), lifetime, maxUses);
    }

    /** An opaque session token for an account, carrying whatever roles it holds now. */
    protected String tokenFor(Account account) {
        return sessions.open(account, "203.0.113.7", "integration-test").token();
    }

    /** How many enrollments the classroom currently has active. */
    protected int activeEnrollmentCount(UUID classroomId) {
        return jdbc.queryForObject(
                "select count(*) from enrollment where classroom_id = ? and ended_at is null",
                Integer.class, classroomId);
    }
}
