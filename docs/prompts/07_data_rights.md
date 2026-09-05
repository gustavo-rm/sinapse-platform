# Prompt 07 — Data subject rights: erasure, export and access disclosure

**Prerequisite:** prompts 01 to 06 complete and verified.
**Scope:** the `datarights` coordinator, plus the erasure and export operations each module
exposes for its own data.
**Out of scope:** the research dataset export pipeline, which anonymises at collection time
and is a separate concern; portability to a competitor's format; anything about the ethics
committee submission.

Read `CLAUDE.md` and `docs/adr/0011-direitos-do-titular.md` before starting.

The migration `V6__data_rights.sql` already exists and is authoritative. It replaces three
trigger functions from earlier migrations — read it before touching any of them.

---

## Task

### 1. Architectural placement

`datarights` is a coordinator, not a bounded context. It is the **second** component in the
system that knows more than one module, alongside `planning.orchestration`.

Each module exposes in its own `api` the erasure and export operations for its own data:

```java
public interface ModuleDataRights {
    void eraseFor(UUID accountId);
    ModuleExport exportFor(UUID accountId);
}
```

`datarights` only sequences these calls inside one transaction. **It must never touch
another module's tables or entities.** If you find yourself writing SQL against
`study_session` from `datarights`, stop — the operation belongs in `learningrecord`.

Add a test that fails when a module holding personal data has no registered implementation.
A module that stores personal data and does not participate in erasure is a silent leak,
and it will happen the first time someone adds a module in a hurry.

### 2. What survives and what is erased

Follow the table in ADR 0011 exactly. Summarised:

**Survives:** `consent_record` with `evidence` and `guardian_id` cleared; ended
`enrollment` rows; the emptied `account` shell with status `ANONYMIZED`.

**Erased:** `guardian`, `user_session`, `account_token`, `study_session`,
`study_availability`, `study_goal`, `study_plan`, `planned_session`,
`plan_generation_request` including its `snapshot`.

Study history is **not** anonymised in place. A longitudinal sequence of timestamped topics
is effectively a behavioural fingerprint and re-identifies when cross-referenced with a
classroom roster. Do not implement a "scrub and keep" path for it, however tempting.

The `snapshot` is the most sensitive artifact in the system: it contains the whole history
in one document. Verify explicitly that it is gone.

### 3. The erasure flag

Set `sinapse.erasure` to `on` with `set_config(..., true)` — transaction-local, never
session-local — at the start of the erasure transaction and never anywhere else.

Exactly one service may set it. Add a test proving that a normal delete on `study_session`
outside that service still fails.

### 4. Lifecycle

On request: suspend the account and revoke all sessions immediately. Create an
`erasure_request` with `effectiveAt` 7 days out.

During the window: the holder may cancel, which reactivates the account.

At `effectiveAt`: a scheduled job performs the erasure in **one transaction**. Partial
erasure is worse than none — if any step fails, the whole thing rolls back and the request
is marked `FAILED` for investigation.

`erasure_request` stores no copy of the erased data and no e-mail. Storing either would
reintroduce exactly what the request removes.

### 5. Export and access disclosure

- **Export:** the holder's own data in a structured format (JSON), assembled from each
  module's `exportFor`. Rate-limited. Delivered through an authenticated download, not
  e-mail.
- **Access disclosure:** which teachers had access to the holder's data and in which
  periods, derived from `enrollment`. Read-only.

---

## Definition of done

All items in `CLAUDE.md` §8, plus:

- Erasure removes every record listed as erased, verified by direct queries after the
  transaction commits
- `consent_record` survives with `evidence` null and every other field unchanged
- Ended `enrollment` rows survive
- The e-mail of an erased account can be used to register a new account
- A delete on `study_session` outside the erasure service still fails
- A forced failure mid-erasure rolls back completely, leaving no partial state
- Cancellation within the window restores the account to `ACTIVE` with its data intact
- The module-coverage test fails when a module is missing its implementation

## Report

In Portuguese: what was implemented, the result of each verification above, how the
rollback test was constructed, and any point where ADR 0011 was ambiguous or wrong.

Work on `feature/1.0/data-subject-rights`. Commit in Conventional Commits format, in
English, following `CLAUDE.md` §4.
