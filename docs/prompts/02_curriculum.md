# Prompt 02 — Curriculum bounded context

**Prerequisite:** prompt 01 complete and verified.
**Scope:** curriculum module only — subject, topic, prerequisite graph, curation
operations.
**Out of scope:** teacher, classroom, enrollment, planning, the optimisation core, any
teacher overlay on the graph, any automatic derivation of edges.

Read `CLAUDE.md`, section 6 of `docs/ARQUITETURA_BACKEND_SINAPSE.md` and
`docs/adr/0006-grafo-de-pre-requisitos.md` before starting.

The migration `V2__curriculum.sql` already exists and is authoritative. Do not modify it;
map the code to it.

---

## Task

### 1. Aggregates

`Subject`, `Topic` and `TopicPrerequisite` are three separate aggregate roots. `Topic` is
**not** an internal entity of `Subject` — it is referenced by identifier from other
contexts and is the primary unit of planning.

`Topic.code` is a stable natural key, unique within the subject. It exists because the
curated catalogue lives in version-controlled CSV, where referencing topics by UUID is
unusable for a human and referencing them by position would silently repoint every edge on a
reorder. Never derive it from the name at runtime.

`curriculum` is a base module. It must not import any other module. `ModularityTests`
enforces this; do not work around it.

**Tighten rule R3 in the build.** Declare `allowedDependencies = {}` on the `curriculum` and
`identity` module descriptors, so the base layer having no outgoing dependency is verified
rather than merely documented. Prompt 00 deliberately left this out while the modules were
empty.

### 2. Prerequisite edges

Directed edge: `prerequisiteTopicId` precedes `dependentTopicId`.

`strength`: `HARD` or `SOFT`. `provenance`: `CURATED`, `TEXTBOOK_ORDER` or `DERIVED`.
There is no `TEACHER` value — do not add one, it would violate rule R3.

Edges may cross subjects. Do not add validation that restricts an edge to a single
subject.

### 3. Effort tier

`Topic.effortTier` is mandatory: `SHORT`, `STANDARD`, `LONG` or `EXTENDED`.

It is an ordinal band, **not minutes**. Do not add a minutes field, do not derive minutes
here, and do not let the student set it. The band-to-minutes mapping is configuration read by
the snapshot assembly in prompt 06. See ADR 0012.

Expose the mapping as configuration with declared initial values, marked in code as an
assumption to be calibrated against pilot data.

### 4. Curation operations

**Manual edge creation and correction.** Edges are curated data, not legal records. Unlike
consent and enrollment, they may be updated and deleted. Keep `createdBy` and `createdAt`.

**Textbook-order seeding.** A bulk operation that, for a given subject, creates `SOFT`
edges with provenance `TEXTBOOK_ORDER` between consecutive topics ordered by `position`.
It must be **idempotent**: running it twice produces the same graph, and it must never
overwrite an existing `CURATED` edge for the same pair.

**Topic reordering.** Rewriting `position` for all topics of a subject in a single
transaction. This is why `uq_topic_position` is deferrable; make sure the transaction
boundary actually allows it.

### 5. Graph queries exposed from `curriculum.api`

- All edges for a set of subjects, with `strength` and `provenance`
- Topological order for a set of topics, or a clear failure if it cannot be produced
- Direct prerequisites of a topic

These are what the orchestration layer will use to build the core snapshot. Return DTOs,
never entities.

### 6. Acyclicity — verify, do not trust

The database enforces acyclicity. Your job is to prove it does, at the database level:

1. Insert edges A→B and B→C, then attempt C→A. Confirm rejection and report the message.
2. Attempt a self-edge. Confirm rejection.
3. Update an existing edge so that it would close a cycle. Confirm the trigger also fires
   on `UPDATE`, not only on `INSERT`.
4. **Concurrency test.** Two transactions, each inserting one edge, where neither closes a
   cycle alone but together they would. Run them concurrently and confirm exactly one
   fails. This is the reason the trigger takes an advisory lock; a test that never
   exercises concurrency proves nothing about it.

Test 4 needs real concurrent connections, not two sequential calls in one transaction.

Map the database exception to a domain exception with a clear message. Do not let a raw
PostgreSQL error reach the API response.

---

## Definition of done

All items in `CLAUDE.md` §8, plus the tests listed under "Contexto de Currículo" in
section 9 of `docs/ARQUITETURA_BACKEND_SINAPSE.md`, plus the four verifications above with
their observed output reported.

## Report

In Portuguese: what was implemented, the observed rejection messages, how the concurrency
test was built and what it produced, and any decision you had to make that was not
specified here.

Work on `feature/1.0/curriculum-context`. Commit in Conventional Commits format, in
English, scoped to `curriculum`, following `CLAUDE.md` §4.
