# Prompt 03 — Educational bounded context

**Prerequisite:** prompts 01 and 02 complete and verified.
**Scope:** educational module only — teacher, classroom, invite, enrollment, derived
authorisation.
**Out of scope:** institutions, multi-tenancy, teacher feedback instrument (P3), how an
account is granted the `TEACHER` role (P2), teacher overlay on the prerequisite graph,
planning, learning record.

Read `CLAUDE.md`, section 7 of `docs/ARQUITETURA_BACKEND_SINAPSE.md` and
`docs/adr/0005-acesso-do-professor.md` before starting.

The migration `V3__educational.sql` already exists and is authoritative. Do not modify it.

---

## Task

### 1. Aggregates

`Teacher`, `Classroom`, `Invite` and `Enrollment` are four separate roots. `Invite` and
`Enrollment` are separate because both are queried without loading the classroom: invites
are looked up by code by a student who has no access to the classroom yet, and enrollment
is read on every authorisation check.

### 2. Invites

Code: 10 characters, Crockford base32 alphabet, excluding `I`, `L`, `O` and `U`. Generate
with a cryptographically secure source. Stored in clear text — this is deliberate, because
the teacher must be able to display the code again after creation.

Mandatory expiry, default 14 days. Optional use limit.

**Rate limit redemption attempts, per account and per origin.** Without it the code
entropy protects nothing. This is not optional and is not a later hardening step.

### 3. Invariants

Each needs a test that fails when violated:

1. An invite is redeemable only if active, unexpired, within the use limit, and the
   classroom is `OPEN`.
2. Redemption requires an `ACTIVE` account **and** valid `INSTITUTION_SHARING` consent.
   Consult `AccountAccessPolicy` from `identity.api`; do not query identity tables
   directly.
3. At most one active enrollment per (account, classroom).
4. A teacher cannot enroll in their own classroom. This one spans aggregates and is
   enforced in application code — the database does not check it.
5. Enrollment is never deleted. Ending it means writing `endedAt` and `endedReason`.
6. Archiving a classroom ends all active enrollments and revokes pending invites, in the
   same transaction.

### 4. Derived authorisation

```java
public interface TeacherAccessPolicy {
    boolean canViewStudent(UUID teacherAccountId, UUID studentAccountId);
    VisibilityScope scopeFor(UUID teacherAccountId, UUID studentAccountId);
}
```

`VisibilityScope` is `ALL` or `SUBJECTS(Set<UUID>)`. It returns `ALL` today. Model it as a
sealed type anyway — it is the single reversal point for decision P1 and must not require
a code sweep to change.

`canViewStudent` is true when there is an active enrollment of the student in a classroom
owned by that teacher **and** the student's `INSTITUTION_SHARING` consent is currently
valid.

Consent is checked at query time. Do not implement event propagation from `identity` — it
would invert the dependency direction and would produce worse behaviour, since
re-consenting would not restore an ended enrollment. This was decided in ADR 0005.

### 5. Endpoints

Classroom creation and archiving, invite creation, listing, revocation, invite redemption
by a student, listing one's own enrollments, listing a classroom's students, and a
teacher leaving or removing a student.

Invite redemption requires a preview endpoint: given a code, return the classroom name,
the teacher name and **exactly what the teacher will be able to see**. The student accepts
after seeing this. This is what makes `INSTITUTION_SHARING` informed consent given the
integral visibility scope; it is a requirement, not a UI nicety.

### 6. `TEACHER` role — do not build

P2 is unresolved: how an account is granted the `TEACHER` role. Assume the role already
exists on the account and check it. Do not build a registration or verification flow for
teachers. If a task seems to require one, stop and report.

---

## Definition of done

All items in `CLAUDE.md` §8, plus the tests listed under "Contexto educacional" in section
9 of `docs/ARQUITETURA_BACKEND_SINAPSE.md`.

Verify at the database level: the partial index rejects a second active enrollment, and
the trigger rejects a `DELETE` on enrollment.

Verify the rate limit actually blocks: repeated redemption attempts with invalid codes
must be rejected before reaching the lookup.

## Report

In Portuguese: what was implemented, which invariants have tests, how the rate limit was
implemented and how you verified it, and any decision you had to make that was not
specified here.

Work on `feature/1.0/educational-context`. Commit in Conventional Commits format, in
English, scoped to `educational`, following `CLAUDE.md` §4.
