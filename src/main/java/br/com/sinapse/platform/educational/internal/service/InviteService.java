package br.com.sinapse.platform.educational.internal.service;

import br.com.sinapse.platform.curriculum.api.CurriculumCatalog;
import br.com.sinapse.platform.curriculum.api.SubjectView;
import br.com.sinapse.platform.educational.api.EnrollmentView;
import br.com.sinapse.platform.educational.internal.config.EducationalProperties;
import br.com.sinapse.platform.educational.internal.domain.Classroom;
import br.com.sinapse.platform.educational.internal.domain.Enrollment;
import br.com.sinapse.platform.educational.internal.domain.Invite;
import br.com.sinapse.platform.educational.internal.domain.Teacher;
import br.com.sinapse.platform.educational.internal.error.AlreadyEnrolledException;
import br.com.sinapse.platform.educational.internal.error.InviteLifetimeOutOfRangeException;
import br.com.sinapse.platform.educational.internal.error.InviteNotRedeemableException;
import br.com.sinapse.platform.educational.internal.error.NotTheClassroomOwnerException;
import br.com.sinapse.platform.educational.internal.error.SharingConsentRequiredException;
import br.com.sinapse.platform.educational.internal.error.TeacherCannotEnrollException;
import br.com.sinapse.platform.educational.internal.persistence.ClassroomRepository;
import br.com.sinapse.platform.educational.internal.persistence.EnrollmentRepository;
import br.com.sinapse.platform.educational.internal.persistence.InviteRepository;
import br.com.sinapse.platform.educational.internal.persistence.TeacherRepository;
import br.com.sinapse.platform.identity.api.AccountAccessPolicy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issuing, showing, revoking and redeeming invites.
 *
 * <p>Redemption is where the module's rules meet. Six conditions have to hold, and each has a
 * reason to be checked here rather than somewhere more convenient:
 *
 * <ol>
 *   <li>the code resolves to an invite — normalised first, so a student who typed the letter
 *       that looks like the digit is not turned away;</li>
 *   <li>the invite is not revoked, not expired and not exhausted;</li>
 *   <li>its classroom is still open;</li>
 *   <li>the account may share its data with an institution, which is one question asked of
 *       {@code identity.api} and covers both being active and having consented;</li>
 *   <li>the account is not the teacher who owns the classroom;</li>
 *   <li>the account is not already in it.</li>
 * </ol>
 *
 * <p>The first three answer identically when they fail, and deliberately: a code is fifty bits
 * of entropy stored in clear text, and an answer that separated "no such code" from "that code
 * expired" would hand a guesser the signal the entropy exists to deny them. Rate limiting on
 * this route is the other half of that trade-off, and it runs in the filter chain, before this
 * class is reached at all.
 *
 * <p>Condition five is invariant 4, and it is the only one the database does not check. It
 * spans two aggregates and its failure mode does not corrupt a legal record, so it lives here
 * — which means the test for it is the only thing standing behind it.
 */
@Service
public class InviteService {

    private final InviteRepository invites;
    private final ClassroomRepository classrooms;
    private final EnrollmentRepository enrollments;
    private final TeacherRepository teachers;
    private final TeacherDirectory teacherDirectory;
    private final ClassroomService classroomService;
    private final AccountAccessPolicy accounts;
    private final CurriculumCatalog catalog;
    private final EducationalProperties properties;
    private final Clock clock;

    /**
     * @param invites          invites
     * @param classrooms       classrooms
     * @param enrollments      enrollments
     * @param teachers         teacher records
     * @param teacherDirectory resolution of the calling teacher
     * @param classroomService ownership checks
     * @param accounts         the access gate of the identity module
     * @param catalog          the curriculum catalogue, for the subject names in a preview
     * @param properties       configured invite lifetimes
     * @param clock            application clock
     */
    public InviteService(InviteRepository invites, ClassroomRepository classrooms,
            EnrollmentRepository enrollments, TeacherRepository teachers,
            TeacherDirectory teacherDirectory, ClassroomService classroomService,
            AccountAccessPolicy accounts, CurriculumCatalog catalog,
            EducationalProperties properties, Clock clock) {
        this.invites = invites;
        this.classrooms = classrooms;
        this.enrollments = enrollments;
        this.teachers = teachers;
        this.teacherDirectory = teacherDirectory;
        this.classroomService = classroomService;
        this.accounts = accounts;
        this.catalog = catalog;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Issues an invite for a classroom.
     *
     * @param teacherAccountId account of the teacher, who must own the classroom
     * @param classroomId      classroom the code will admit to
     * @param lifetime         how long it should last, or {@code null} for the configured
     *                         default. Never unbounded
     * @param maxUses          how many students may redeem it, or {@code null} for no limit
     * @return the invite, with its code in clear so the teacher can show it
     * @throws NotTheClassroomOwnerException if the classroom is not theirs
     */
    @Transactional
    public Invite issue(UUID teacherAccountId, UUID classroomId, Duration lifetime, Integer maxUses) {
        Classroom classroom = classroomService.requireOwned(teacherAccountId, classroomId);
        if (!classroom.isOpen()) {
            // An archived classroom admits nobody, so a code for one is a code that can only
            // disappoint whoever is handed it.
            throw new InviteNotRedeemableException();
        }
        Teacher teacher = teacherDirectory.require(teacherAccountId);

        Instant now = clock.instant();
        Duration effective = lifetime == null ? properties.defaultInviteLifetime() : lifetime;
        if (effective.isNegative() || effective.isZero()
                || effective.compareTo(properties.maxInviteLifetime()) > 0) {
            throw new InviteLifetimeOutOfRangeException();
        }

        return invites.save(new Invite(UUID.randomUUID(), classroomId, freshCode(), teacher.id(),
                now, now.plus(effective), maxUses));
    }

    /**
     * The invites of a classroom, newest first, spent and revoked ones included.
     *
     * @param teacherAccountId account of the teacher, who must own the classroom
     * @param classroomId      classroom
     * @return its invites
     */
    @Transactional(readOnly = true)
    public List<Invite> listFor(UUID teacherAccountId, UUID classroomId) {
        classroomService.requireOwned(teacherAccountId, classroomId);
        return invites.findByClassroomIdOrderByCreatedAtDesc(classroomId);
    }

    /**
     * Revokes an invite.
     *
     * @param teacherAccountId account of the teacher, who must own its classroom
     * @param inviteId         invite to revoke
     */
    @Transactional
    public void revoke(UUID teacherAccountId, UUID inviteId) {
        Invite invite = invites.findById(inviteId).orElseThrow(NotTheClassroomOwnerException::new);
        classroomService.requireOwned(teacherAccountId, invite.classroomId());
        invite.revoke(clock.instant());
    }

    /**
     * What a student is shown before deciding.
     *
     * <p>This is a requirement of ADR 0005, not a courtesy of the interface. The scope adopted
     * is integral — the teacher will see the student's whole history and plan, not the slice
     * belonging to this classroom's subjects — and a consent to that which is not shown at the
     * moment of the decision is not informed. The cost in data minimisation was accepted on
     * the condition that this screen exists.
     *
     * <p>It reads nothing from identity. The teacher's name is on the teacher record, which is
     * exactly why that record carries one.
     *
     * @param code code the student presented
     * @return what the invite admits to and what accepting it discloses
     * @throws InviteNotRedeemableException if the code admits to nothing, for any reason
     */
    @Transactional(readOnly = true)
    public InvitePreview preview(String code) {
        Invite invite = redeemableInvite(code);
        Classroom classroom = classrooms.findById(invite.classroomId())
                .orElseThrow(InviteNotRedeemableException::new);
        Teacher teacher = teachers.findById(invite.createdBy())
                .orElseThrow(InviteNotRedeemableException::new);

        // The catalogue is a curated global list of the order of dozens, read whole and
        // filtered here rather than through a lookup curriculum does not offer. If it ever
        // grows to where that matters, the answer is a batch lookup by identifier there, not
        // a join across the boundary from here.
        List<String> subjectNames = catalog.subjects().stream()
                .filter(subject -> classroom.subjectIds().contains(subject.id()))
                .map(SubjectView::name)
                .toList();

        return new InvitePreview(classroom.id(), classroom.name(), teacher.displayName(),
                teacher.institutionName(), subjectNames, invite.expiresAt());
    }

    /**
     * Redeems an invite.
     *
     * @param accountId account redeeming it
     * @param code      code presented
     * @return the enrollment
     * @throws InviteNotRedeemableException    if the code admits to nothing
     * @throws SharingConsentRequiredException if the account may not share with an institution
     * @throws TeacherCannotEnrollException    if the account owns the classroom
     * @throws AlreadyEnrolledException        if it is already in the classroom
     */
    @Transactional
    public EnrollmentView redeem(UUID accountId, String code) {
        Invite invite = redeemableInvite(code);
        Classroom classroom = classrooms.findById(invite.classroomId())
                .filter(Classroom::isOpen)
                .orElseThrow(InviteNotRedeemableException::new);

        if (!accounts.canShareWithInstitution(accountId)) {
            throw new SharingConsentRequiredException();
        }
        teachers.findById(classroom.teacherId())
                .filter(owner -> owner.accountId().equals(accountId))
                .ifPresent(owner -> {
                    throw new TeacherCannotEnrollException();
                });
        enrollments.findByClassroomIdAndAccountIdAndEndedAtIsNull(classroom.id(), accountId)
                .ifPresent(existing -> {
                    throw new AlreadyEnrolledException();
                });

        Instant now = clock.instant();
        invite.recordRedemption(now);
        try {
            // Flushed inside the call so that the partial unique index answers here. Two
            // requests redeeming at once both pass the check above and only one may land; the
            // index is what decides, and this is where its refusal becomes an answer.
            Enrollment enrollment = enrollments.saveAndFlush(new Enrollment(UUID.randomUUID(),
                    classroom.id(), accountId, invite.id(), now));
            return EducationalViews.of(enrollment);
        } catch (DataIntegrityViolationException raced) {
            throw new AlreadyEnrolledException();
        }
    }

    /**
     * Resolves a presented code to an invite that is still good.
     *
     * <p>Every way of failing raises the same exception. See the class comment: the code is
     * stored in clear, so the answer must not tell a guesser how close they were.
     */
    private Invite redeemableInvite(String code) {
        Instant now = clock.instant();
        return invites.findByCode(InviteCodes.normalise(code))
                .filter(invite -> invite.isRedeemableAt(now))
                .filter(invite -> classrooms.findById(invite.classroomId())
                        .map(Classroom::isOpen)
                        .orElse(false))
                .orElseThrow(InviteNotRedeemableException::new);
    }

    /**
     * Draws a code nobody has.
     *
     * <p>A collision over fifty bits is not something that happens; the check is here because
     * the alternative to one query is an integrity violation the teacher would have to
     * interpret.
     */
    private String freshCode() {
        for (int attempt = 0; attempt < 5; attempt++) {
            String candidate = InviteCodes.generate();
            if (!invites.existsByCode(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not draw an unused invite code in five attempts");
    }

    /**
     * What a student sees before accepting.
     *
     * @param classroomId     classroom the code admits to
     * @param classroomName   its name
     * @param teacherName     the teacher who will gain access. Personal data
     * @param institutionName their institution as free text, or {@code null}
     * @param subjectNames    subjects that carry an institutional deadline here
     * @param expiresAt       when the code stops working
     */
    public record InvitePreview(
            UUID classroomId,
            String classroomName,
            String teacherName,
            String institutionName,
            List<String> subjectNames,
            Instant expiresAt) {

        /** Copies the list so a preview cannot change after it was produced. */
        public InvitePreview {
            subjectNames = List.copyOf(subjectNames);
        }
    }
}
