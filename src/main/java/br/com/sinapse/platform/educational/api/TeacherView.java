package br.com.sinapse.platform.educational.api;

import java.util.UUID;

/**
 * A teacher, as other modules and clients see them.
 *
 * <p>Published because two callers need a name to put next to a classroom: the invite preview,
 * which has to tell a student who they would be disclosing their data to, and the Article 18
 * disclosure, which has to tell them who already could.
 *
 * <p>Only the name and the institution. Everything else about the person is their account,
 * which belongs to identity and is not this module's to hand out.
 *
 * @param id              identifier of the teacher record
 * @param accountId       account behind it
 * @param displayName     name a student sees. Emptied if the teacher's own data has been erased
 * @param institutionName institution they named, or {@code null}
 */
public record TeacherView(UUID id, UUID accountId, String displayName, String institutionName) {
}
