package br.com.sinapse.platform.educational.internal.service;

import br.com.sinapse.platform.educational.internal.domain.Teacher;
import br.com.sinapse.platform.educational.internal.error.TeacherRecordMissingException;
import br.com.sinapse.platform.educational.internal.persistence.TeacherRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The teacher record behind an account that owns classrooms.
 *
 * <p><strong>There is no registration flow here, and that is deliberate.</strong> How an
 * account comes to hold the {@code TEACHER} role is unresolved (P2), and the recommendation on
 * record is a manual, audited administrative operation rather than self-registration — because
 * self-registration would let anyone open a classroom and issue invites. Building a flow now
 * would be guessing at that answer.
 *
 * <p>What exists instead is {@link #record(UUID, String, String)}: the seam that operation will
 * call, with no route in front of it. The role authorises the login; this record carries the
 * display name a student sees when deciding whether to accept an invite, which is why it
 * cannot be created implicitly — identity stores no name for anybody, so there is nothing to
 * derive one from.
 */
@Service
public class TeacherDirectory {

    private final TeacherRepository teachers;
    private final Clock clock;

    /**
     * @param teachers teacher records
     * @param clock    application clock
     */
    public TeacherDirectory(TeacherRepository teachers, Clock clock) {
        this.teachers = teachers;
        this.clock = clock;
    }

    /**
     * Records, or corrects, the professional link of an account.
     *
     * <p>Not reachable over HTTP. It is called by tests and, when P2 is answered, by whatever
     * administrative operation that answer defines.
     *
     * @param accountId       account that holds the teacher role
     * @param displayName     name a student will see on an invite preview
     * @param institutionName institution as free text, or {@code null}
     * @return the record
     */
    @Transactional
    public Teacher record(UUID accountId, String displayName, String institutionName) {
        Teacher teacher = teachers.findByAccountId(accountId).orElse(null);
        if (teacher == null) {
            return teachers.save(new Teacher(UUID.randomUUID(), accountId, displayName,
                    institutionName, clock.instant()));
        }
        teacher.reviseTo(displayName, institutionName);
        return teacher;
    }

    /**
     * The teacher record of the calling account.
     *
     * @param accountId account holding the teacher role
     * @return the record
     * @throws TeacherRecordMissingException if the account holds the role but has no record,
     *                                       which means the administrative operation that
     *                                       grants one was not completed
     */
    @Transactional(readOnly = true)
    public Teacher require(UUID accountId) {
        return teachers.findByAccountId(accountId).orElseThrow(TeacherRecordMissingException::new);
    }
}
