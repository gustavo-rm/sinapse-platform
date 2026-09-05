# Prompt 05 — Planning bounded context

**Prerequisite:** prompt 04 complete and verified.
**Scope:** planning module only — availability, goals, plan and planned session as
persisted structures.
**Out of scope:** the generation job execution, the core client, the snapshot assembly and
the orchestration layer. Those are prompt 06. Do not anticipate them.

Read `CLAUDE.md`, section 9 of `docs/ARQUITETURA_BACKEND_SINAPSE.md` and
`docs/adr/0007-contrato-do-core-e-reprodutibilidade.md` before starting.

The migration `V5__planning.sql` already exists and is authoritative. It creates
`plan_generation_request` as well; in this prompt you only map it as a persisted entity,
you do not execute anything.

---

## Task

### 1. Availability

Recurring weekly windows with a validity range (`effectiveFrom`, `effectiveUntil`).

The validity range exists so that a change of routine does not destroy the availability a
past plan was generated against. Do not model availability as mutable rows overwritten on
edit: editing means closing the current window and opening a new one.

Overlapping windows on the same weekday within the same validity range must be rejected.
The database does not check this; enforce it in the application and test it.

### 2. Goals

A goal references a subject, never individual topics. All topics of the subject are in
scope; the optimisation core decides order and what fits the horizon. Do not build topic
exclusion — decision L3, ADR 0012.

Subject, optional target date, priority 1 to 5, status. At most one `ACTIVE` goal per
account and subject, enforced by partial index.

The target date is a prioritisation constraint, not the plan horizon. See F3 in ADR 0012.

### 3. Plan and planned sessions

`StudyPlan` is the root; `PlannedSession` are internal entities of the plan. This is the
opposite choice from `Topic` in `curriculum`, and it is deliberate: planned sessions are
never queried without their plan, and the plan is the unit of immutability.

Plans are immutable. `PlannedSession` rejects any update or delete by trigger. `StudyPlan`
accepts changes only to the supersession fields.

At most one `ACTIVE` plan per account, enforced by partial index.

Supersession: the previous plan moves to `SUPERSEDED`, records `supersededAt` and points to
its successor. Its planned sessions are preserved untouched — executed sessions reference
them.

### 4. Queries exposed from `planning.api`

- Current active plan for an account
- Planned sessions within a time window
- Plan history for an account, ordered, with the supersession chain
- Current availability and active goals for an account

Return DTOs, never entities.

### 5. Endpoints

Availability management, goal management, reading the active plan, reading planned sessions
for a date range, and reading plan history.

**No endpoint that creates or edits a plan directly.** Plans come only from the generation
job, which is prompt 06. If a task seems to require creating a plan by hand, stop and
report.

---

## Definition of done

All items in `CLAUDE.md` §8, plus the tests listed under "Planejamento" in section 11 of
`docs/ARQUITETURA_BACKEND_SINAPSE.md` that do not depend on job execution.

Verify at the database level: the partial indexes reject a second active plan and a second
active goal per subject; the triggers reject mutation of `planned_session` and of the
immutable fields of `study_plan`.

## Report

In Portuguese: what was implemented, which invariants have tests, how overlapping
availability windows are detected, and any decision you had to make that was not specified
here.

Work on `feature/1.0/planning-context`. Commit in Conventional Commits format, in English,
scoped to `planning`, following `CLAUDE.md` §4.
