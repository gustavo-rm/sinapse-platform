# Prompt 08 — Read models and API contract

**Prerequisite:** prompts 01 to 07 complete and verified.
**Scope:** the `readmodel` component, the six read models, and the batch lookup methods each
module must expose to support them.
**Out of scope:** materialised views, caching, asynchronous projections, generic pagination,
configurable sorting and filtering, any endpoint not listed in
`docs/CONTRATO_API_SINAPSE.md`.

Read `CLAUDE.md`, `docs/CONTRATO_API_SINAPSE.md` and
`docs/adr/0013-servico-de-leitura.md` before starting.

---

## Task

### 1. Batch lookups first

Before writing any read model, add batch lookup methods to each module's `api`:

```java
Map<UUID, TopicView> findTopics(Set<UUID> topicIds);
```

Composing across module boundaries without joins creates an N+1 risk: a day's agenda with 40
sessions would otherwise make 40 topic lookups. The batch method belongs to the module's
contract, not to the caller.

**Add a test that asserts query counts** for each read model. Without it, an N+1 introduced
later passes every functional test and only shows up as latency in production.

### 2. The six read models

Implement exactly the six specified in `docs/CONTRATO_API_SINAPSE.md` §3, with the fields
listed there:

`EstadoDoAluno`, `AgendaDoDia`, `PlanoResumido`, `PreviaDoConvite`, `PainelDoAluno`,
`ListaDaTurma`.

Do not add a seventh. Do not add fields that no listed flow uses.

Two details that are easy to get wrong:

- `PlanoResumido.adherence` counts only planned sessions already past due. A future session
  is not a missed one.
- `EstadoDoAluno.openSessionId` exists because there is at most one in-progress session per
  account; the client must offer to resume rather than start another.

### 3. Placement and boundaries

`readmodel` sits at the same level as `datarights`. It composes by calling each module's
`api`. It must never query another module's tables, never import `internal`, and never
write.

`ModularityTests` must still pass. If a read model seems to require crossing a boundary,
the missing piece is a method on some module's `api` — add it there.

### 4. Authorisation

`PainelDoAluno` and `ListaDaTurma` require `TeacherAccessPolicy.canViewStudent`, which
checks both active enrollment and current `INSTITUTION_SHARING` consent. Consent is checked
at query time, so a student who revokes disappears immediately.

Test that: a teacher reading a panel, the student revoking consent, the same read now
failing, the student re-consenting, the read succeeding again — with no change to the
enrollment.

`EstadoDoAluno` and `AgendaDoDia` are the account holder's own data only.

### 5. No materialisation

Compute on demand. No caching, no materialised views, no projections.

`ListaDaTurma` aggregates history across N students and is the heaviest query in the system.
**Measure it** with a realistic classroom — 40 students, three months of sessions — and
report the timing. Do not optimise it before the measurement; do report if the measurement
says it needs optimising.

### 6. Conventions

All routes under `/api/v1`. RFC 7807 errors. Instants as ISO-8601 with offset. Enum literals
in uppercase — the API returns no display text in Portuguese; translation is the client's
responsibility.

History endpoints require a mandatory time window with a configured maximum span. Small
bounded lists return complete with a hard server-side cap. Do not build a generic pagination
mechanism.

---

## Definition of done

All items in `CLAUDE.md` §8, plus:

- Each read model has a query-count test asserting no N+1
- The consent revocation and re-consent sequence is tested end to end
- `ListaDaTurma` is measured with 40 students and three months of history, timing reported
- `ModularityTests` passes
- OpenAPI annotations on every new endpoint, so the generated specification matches
  `docs/CONTRATO_API_SINAPSE.md`

## Report

In Portuguese: what was implemented, the query counts observed per read model, the
`ListaDaTurma` timing, and any point where the API contract document was ambiguous or wrong.

Work on `feature/1.0/read-models`. Commit in Conventional Commits format, in English,
following `CLAUDE.md` §4.
