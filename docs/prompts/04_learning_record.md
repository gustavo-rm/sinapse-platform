# Prompt 04 — Learning record bounded context

**Prerequisite:** prompt 03 complete and verified.
**Scope:** learningrecord module only — study session lifecycle and history queries.
**Out of scope:** exercises, exercise attempts, flashcards, spaced repetition scheduling,
planning, the optimisation core, any objective retention instrument (decision D4).

Read `CLAUDE.md`, section 8 of `docs/ARQUITETURA_BACKEND_SINAPSE.md` and
`docs/adr/0008-evidencia-de-aprendizagem.md` before starting.

The migration `V4__learning_record.sql` already exists and is authoritative.

---

## Task

### 1. Aggregate

`StudySession` is the single root. It holds account, topic, kind (`STUDY` or `REVISION`),
source (`FROM_PLAN` or `SELF_DIRECTED`), timestamps, durations and a four-level recall
rating.

A self-directed session must be **structurally identical** to a planned one. Do not create
two classes, two endpoints or two code paths for them. The difference is the value of
`source` and the presence of `plannedSessionId`.

### 2. `plannedSessionId` has no foreign key

This is deliberate. Neither `planning` nor `learningrecord` may depend on the other
(rule R2). Store the identifier as a plain `UUID`. Do not import anything from `planning`,
do not add a foreign key, and do not "fix" this. `ModularityTests` will catch an attempt.

### 3. Lifecycle

`IN_PROGRESS` → `COMPLETED` or `ABANDONED`. A session is mutable while in progress and
frozen once closed, enforced by trigger.

Invariants, each needing a test that fails when violated:

1. At most one `IN_PROGRESS` session per account.
2. A closed session cannot be updated.
3. A session cannot be deleted.
4. `recallRating` only on a `COMPLETED` session.
5. `source = FROM_PLAN` if and only if `plannedSessionId` is present.

Invariant 1 protects the most basic evidence in this context: two concurrent sessions would
corrupt the duration record.

### 4. Duration source

`durationSource` is `MEASURED` when the app timed the session and `SELF_REPORTED` when the
student entered it afterwards. It is required whenever `actualDurationMinutes` is present.

Both paths are supported: a timer is the main flow, retroactive entry is the marked
exception. Do not silently mix them — duration is the most basic evidence this context
holds, and the two are not equally reliable. See ADR 0012, decision F5.

### 5. Recall rating

Four ordinal levels: `AGAIN`, `HARD`, `GOOD`, `EASY`. Recorded at session close.

Do not compute a next review date from it. Do not implement any spacing algorithm. This
module records evidence; scheduling belongs to the optimisation core.

### 6. History queries exposed from `learningrecord.api`

- Sessions for an account within a time window
- Sessions for an account grouped by topic, with the recall rating trajectory
- Total effective time per topic within a window
- Adherence: planned sessions executed versus not, over a window

These feed the snapshot built by the orchestration layer. Return DTOs, never entities.

### 7. Consistency job

A scheduled job that detects `plannedSessionId` values with no corresponding planned
session. It reports; it does not delete. Orphan references are a known accepted cost of the
missing foreign key, and silently removing evidence would be worse than the orphan.

This job may query across modules and belongs outside `learningrecord.internal`.

---

## Definition of done

All items in `CLAUDE.md` §8, plus the tests listed under "Registro de aprendizagem" in
section 11 of `docs/ARQUITETURA_BACKEND_SINAPSE.md`.

Verify at the database level: the partial index rejects a second in-progress session, the
trigger rejects an update to a closed session, and the trigger rejects a delete.

## Report

In Portuguese: what was implemented, which invariants have tests, and any decision you had
to make that was not specified here.

Work on `feature/1.0/learning-record-context`. Commit in Conventional Commits format, in
English, scoped to `learningrecord`, following `CLAUDE.md` §4.
