# Sinapse Core contract — survey

Read-only survey of what exists in `sinapse-platform` today, ahead of building a protocol
bridge to `exam-optimizer-application`. Every path and line number below was read from the
working tree; nothing is inferred. Where an item asked for does not exist, it is marked
**NOT FOUND** rather than substituted with a near-match.

Surveyed commit: branch `claude/lucid-brahmagupta-32wseu`, clean working tree.

---

## 1. Locations

### 1.1 Contract records and enums

All six live in one self-contained package, `coreclient/contract/` — **not** under
`coreclient/api/` or `coreclient/internal/`.

| Type | Path |
|---|---|
| `PlanRequest` | `src/main/java/br/com/sinapse/platform/coreclient/contract/PlanRequest.java` |
| `PlanResponse` | `src/main/java/br/com/sinapse/platform/coreclient/contract/PlanResponse.java` |
| `SessionKind` | `src/main/java/br/com/sinapse/platform/coreclient/contract/SessionKind.java` |
| `RecallRating` | `src/main/java/br/com/sinapse/platform/coreclient/contract/RecallRating.java` |
| `EdgeStrength` | `src/main/java/br/com/sinapse/platform/coreclient/contract/EdgeStrength.java` |
| `EdgeProvenance` | `src/main/java/br/com/sinapse/platform/coreclient/contract/EdgeProvenance.java` |

The package is exported as a Spring Modulith named interface `"contract"`
(`coreclient/contract/package-info.java:18`) and imports nothing else in the application,
so it can be lifted out and published as a shared artifact.

Enum values as declared:

- `SessionKind` — `STUDY`, `REVISION` (lines 7, 10)
- `RecallRating` — `AGAIN`, `HARD`, `GOOD`, `EASY` (lines 15, 18, 21, 24)
- `EdgeStrength` — `HARD`, `SOFT` (lines 13, 16)
- `EdgeProvenance` — `CURATED`, `TEXTBOOK_ORDER`, `DERIVED` (lines 13, 16, 19)

### 1.2 REST client and response validation

- **Client:** `src/main/java/br/com/sinapse/platform/coreclient/internal/RestSinapseCore.java`
  — confirmed at the expected path. Implements
  `coreclient/api/SinapseCore.java` (single method `PlanResponse generate(PlanRequest)`).
- **Validation method:** `validated` — confirmed. `RestSinapseCore.java:96`,
  `private static PlanResponse validated(PlanRequest request, PlanResponse response)`.
  Called from `generate` at `RestSinapseCore.java:74`, after the HTTP exchange returns.
  It delegates to two private helpers: `validateMetadata` (line 116) and `validateSessions`
  (line 126).
- **HTTP status handling** is *not* in `validated`; it is in `read`
  (`RestSinapseCore.java:77-94`): 4xx → `CoreProtocolException`, other non-2xx →
  `CoreUnavailableException`, unreadable body → `CoreProtocolException`.
- **Configuration:** `coreclient/internal/CoreProperties.java`,
  `coreclient/internal/CoreClientConfiguration.java`.
- **Exceptions:** `coreclient/api/CoreException.java`,
  `coreclient/api/CoreProtocolException.java`, `coreclient/api/CoreUnavailableException.java`.

### 1.3 Contract version constant

Confirmed as expected: `PlanRequest.VERSION`.

```java
// coreclient/contract/PlanRequest.java:50
public static final String VERSION = "1.0";
```

Read in exactly two places:
- `SnapshotAssembler.java:117` — written into the outgoing `contractVersion` field.
- `RestSinapseCore.java:100` and `:103` — compared against the response's `contractVersion`.

### 1.4 Generation job: orchestration and persistence

**Orchestration** — `src/main/java/br/com/sinapse/platform/planning/orchestration/`:

| Component | Path | Role |
|---|---|---|
| `PlanGenerationOrchestrator` | `planning/orchestration/PlanGenerationOrchestrator.java` | Runs one job end to end: assemble → record → call → write (`run`, line 83) |
| `SnapshotAssembler` | `planning/orchestration/SnapshotAssembler.java` | Builds the `PlanRequest` from four modules (`assemble`, line 94) |
| `GeneratedPlanWriter` | `planning/orchestration/GeneratedPlanWriter.java` | The single write transaction (`store`, line 49) |
| `PlanGenerationWorker` | `planning/orchestration/PlanGenerationWorker.java` | Polls for claimable jobs |
| `SeedSource` / `RandomSeedSource` | `planning/orchestration/SeedSource.java`, `RandomSeedSource.java` | `SecureRandom.nextLong()` (`RandomSeedSource.java:21`) |
| `EffortCalibration` | `planning/orchestration/EffortCalibration.java` | Per-student effort factor, derived at assembly, never persisted |
| `GenerationRequestController` | `planning/orchestration/GenerationRequestController.java` | `POST /api/v1/study-plans/generation-requests` |

**Persistence of the four reproducibility fields.** All four are written to
`plan_generation_request`, on the *job*, not on the plan.

- Entity: `planning/internal/domain/PlanGenerationRequest.java`
  - `snapshot` — `Map<String, Object>`, column `snapshot` (lines 64-65)
  - `coreVersion` — `String`, column `core_version` (lines 67-68)
  - `algorithmParams` — `Map<String, Object>`, column `algorithm_params` (lines 71-72)
  - `randomSeed` — `Long`, column `random_seed` (lines 74-75)
- Table: `src/main/resources/db/migration/V5__planning.sql:68-93`; the four columns are
  declared at lines 74-77, all nullable. The block comment at lines 58-66 states the ADR 0007
  rationale verbatim. The table also carries `catalog_import_id` (line 82), which is a fifth
  attribution field not named in the brief.
- Write path, three fields at once, **before** the HTTP call:
  `PlanGenerationOrchestrator.java:86-87` calls
  `requests.recordSubmission(job.id(), objectMapper.convertValue(request, DOCUMENT), request.algorithmParams(), request.randomSeed())`
  → `GenerationRequestService.recordSubmission` (line 130) →
  `PlanGenerationRequest.recordSubmission` (line 237).
- Write path, `core_version`, **after** the call and inside the plan-write transaction:
  `GeneratedPlanWriter.java:56` calls `requests.succeed(job.id(), response.metadata().coreVersion())`
  → `GenerationRequestService.succeed` (line 145) → `PlanGenerationRequest.succeed` (line 254).
- The snapshot stored is the `PlanRequest` object converted by the application's own
  `ObjectMapper` (`PlanGenerationOrchestrator.java:45`, `:86`), which is the same mapper the
  `RestClient` serialises the body with (`CoreClientConfiguration.java:32`, `builder.clone()`).
  It is not reassembled from entities.

### 1.5 Read model for `GET /study-plans/current`

**NOT FOUND as a read model.** There is no component in the `readmodel` module for this
route. The endpoint exists, but it is served by the `planning` module's own controller:

- Route constant: `planning/internal/web/PlanningRoutes.java:33` —
  `CURRENT_PLAN = STUDY_PLANS + "/current"`, where `STUDY_PLANS = ApiPaths.V1 + "/study-plans"`
  (line 27). Effective path: `/api/v1/study-plans/current`.
- Handler: `planning/internal/web/StudyPlanController.java:62-79`, method `current()`.
  It calls `access.requireProcessable(accountId)` then
  `directory.activePlanOf(accountId)`, mapping through `StudyPlanResponse::of`, and throws
  `UnknownPlanException` when absent.
- Response DTO: `planning/internal/web/StudyPlanResponse.java` — `id`,
  `generationRequestId`, `horizonStart`, `horizonEnd`, `status`, `fitness`, `createdAt`,
  `supersededAt`, `supersededByPlanId`.

The `readmodel` module's nearest artifact is a *different* route:
`readmodel/internal/web/ReadModelRoutes.java:33` —
`PLAN_SUMMARY_TEMPLATE = ApiPaths.V1 + "/study-plans/{planId}/summary"`, served by
`readmodel/internal/web/PlanSummaryController.java:64` (`summary(UUID planId)`) via
`readmodel/internal/service/PlanSummaryReadModel.java`, returning
`readmodel/api/PlanSummaryView.java`. It takes an explicit plan id and does not resolve
"current".

### 1.6 ADR directory and the four requested ADRs

Directory: `docs/adr/` — 14 ADRs, `0001` through `0014`, all in Portuguese.

| ADR | Path | Title |
|---|---|---|
| 0002 | `docs/adr/0002-integracao-sinapse-core.md` | Integração com o Sinapse Core |
| 0006 | `docs/adr/0006-grafo-de-pre-requisitos.md` | Grafo de pré-requisitos entre tópicos |
| 0007 | `docs/adr/0007-contrato-do-core-e-reprodutibilidade.md` | Contrato do Core, job de geração e reprodutibilidade |
| 0012 | `docs/adr/0012-decisoes-de-produto.md` | Decisões de produto derivadas dos fluxos de usuário |

All four dated 04 September 2026, status `aceita`. ADR 0007 declares itself a complement to
ADR 0002. ADR 0012 declares that it resolves F1–F7, L1 and L3 of
`docs/FLUXOS_DE_USUARIO_SINAPSE.md`.

### 1.7 `SINAPSE_CORE_URL` and timeouts

Read in one place, as a Spring placeholder:

```yaml
# src/main/resources/application.yml:286-297
  core:
    base-url: ${SINAPSE_CORE_URL:http://localhost:8090}   # line 291
    plan-path: /plans                                      # line 292
    connect-timeout: 5s                                    # line 293
    read-timeout: 10m                                      # line 297
```

Bound to `coreclient/internal/CoreProperties.java`
(`@ConfigurationProperties("sinapse.core")`, line 26), whose in-code defaults duplicate the
same values: `baseUrl` `http://localhost:8090` (line 29), `planPath` `/plans` (line 31),
`connectTimeout` `5s` (line 33), `readTimeout` `10m` (line 35).

Applied to the HTTP client in `coreclient/internal/CoreClientConfiguration.java:38-43`, on a
`SimpleClientHttpRequestFactory`: `setConnectTimeout` (line 40), `setReadTimeout` (line 41).
The base URL is set at line 33.

`application-local.yml` does **not** override `sinapse.core`; there is no `core:` key in it.

The platform therefore calls **`POST {SINAPSE_CORE_URL}/plans`** today
(`RestSinapseCore.java:61-66`: `restClient.post().uri(properties.planPath())`, content type
and accept both `application/json`).

### 1.8 `effortTier` → minutes, and the assumption comment

`src/main/resources/application.yml:357-370`, under `sinapse.curriculum`:

```yaml
  curriculum:
    # How long a topic of each effort band is planned for (ADR 0012). The band is what a
    # curator judges; the minutes are configuration precisely so that the number can be
    # calibrated against observed data instead of being asserted by whoever curated.
    #
    # THESE VALUES ARE AN ASSUMPTION, NOT A FINDING. They were chosen to be plausible and
    # evenly spaced, and no pilot data has confirmed them. Calibrating them against the
    # durations actually observed in completed study sessions is analysis work belonging to
    # the pilot; until then, read them as a starting point.
    effort-tier-duration:
      SHORT: 25m
      STANDARD: 50m
      LONG: 90m
      EXTENDED: 150m
```

The assumption statement is at lines 362-365; the mapping key is at line 366 and its four
values at lines 367-370.

The same disclaimer is restated in Javadoc on the published interface,
`curriculum/api/EffortTiers.java:13-17` ("**The initial values are an assumption, not a
finding.**"). `EffortTiers` exposes `plannedDurationOf(EffortTier)` (line 31) and
`mapping()` (line 38).

Consumed at `SnapshotAssembler.java:188-191`:

```java
long bandMinutes = effortTiers.plannedDurationOf(topic.effortTier()).toMinutes();
int estimated = Math.max(1, Math.toIntExact(Math.round(bandMinutes * effortFactor)));
return new PlanRequest.Topic(topic.id(), topic.subjectId(), topic.position(),
        topic.effortTier().name(), estimated);
```

A second set of tuning numbers carries an equivalent disclaimer — the effort-factor guards
at `application.yml:332-339` ("THESE ARE A JUDGEMENT, NOT A FINDING"):
`min-calibration-sessions: 5`, `min-effort-factor: 0.5`, `max-effort-factor: 2.0`. The
algorithm parameters sent on every run are at `application.yml:328-331`:
`population-size: 120`, `generations: 400`, `mutation-rate: 0.05`.

---

## 2. Full structure of `PlanRequest` and `PlanResponse`

Transcribed from
`src/main/java/br/com/sinapse/platform/coreclient/contract/PlanRequest.java` and
`PlanResponse.java`. Nullability below is what the code enforces, not what the Javadoc
wishes: `List.copyOf` / `Map.copyOf` in a compact constructor throws `NullPointerException`
on a null argument, so those components are non-null by construction; components with no
check are structurally nullable even where nothing sends null today.

### 2.1 `PlanRequest` (record, 9 components)

| # | Component | Type | Nullable | Notes |
|---|---|---|---|---|
| 1 | `contractVersion` | `String` | yes (unchecked) | Populated with `PlanRequest.VERSION` |
| 2 | `horizon` | `PlanRequest.Horizon` | yes (unchecked) | Nested record |
| 3 | `availability` | `List<PlanRequest.AvailabilitySlot>` | no — `List.copyOf` (line 54) | Immutable copy |
| 4 | `goals` | `List<PlanRequest.Goal>` | no — `List.copyOf` (line 55) | Immutable copy |
| 5 | `topics` | `List<PlanRequest.Topic>` | no — `List.copyOf` (line 56) | Immutable copy |
| 6 | `prerequisites` | `List<PlanRequest.PrerequisiteEdge>` | no — `List.copyOf` (line 57) | Immutable copy |
| 7 | `history` | `List<PlanRequest.TopicHistory>` | no — `List.copyOf` (line 58) | Immutable copy |
| 8 | `algorithmParams` | `Map<String, Object>` | no — `Map.copyOf` (line 59) | Opaque to this side |
| 9 | `randomSeed` | `long` | n/a (primitive) | Chosen by the backend |

Constant: `public static final String VERSION = "1.0"` (line 50).
Compact constructor: lines 53-60, defensive copies only — no validation, no rejection.

**Nested: `PlanRequest.Horizon`** (line 68)

| Component | Type | Nullable |
|---|---|---|
| `start` | `java.time.LocalDate` | yes (unchecked) — first day, inclusive |
| `end` | `java.time.LocalDate` | yes (unchecked) — last day |

**Nested: `PlanRequest.AvailabilitySlot`** (line 89)

| Component | Type | Nullable |
|---|---|---|
| `start` | `java.time.Instant` | yes (unchecked) |
| `end` | `java.time.Instant` | yes (unchecked) |

Absolute instants. The Javadoc (lines 74-84) states these are already expanded from the
student's recurring weekly windows, resolved in the student's own zone by the backend.

**Nested: `PlanRequest.Goal`** (line 103)

| Component | Type | Nullable |
|---|---|---|
| `subjectId` | `java.util.UUID` | yes (unchecked) |
| `targetDate` | `java.time.LocalDate` | **yes, by contract** — Javadoc line 99: "or `null`" |
| `priority` | `int` | n/a (primitive) — documented 1 to 5, higher = more pressing; **not validated in the record** |

Note: the goal's own identifier is not sent — only `subjectId`.

**Nested: `PlanRequest.Topic`** (line 121)

| Component | Type | Nullable |
|---|---|---|
| `id` | `java.util.UUID` | yes (unchecked) |
| `subjectId` | `java.util.UUID` | yes (unchecked) |
| `position` | `int` | n/a (primitive) — curricular position within the subject |
| `effortTier` | `String` | yes (unchecked) — the ordinal band, sent as a plain string, **not** the `EffortTier` enum |
| `estimatedMinutes` | `int` | n/a (primitive) — band minutes scaled by the per-student effort factor |

Topic `code` and `name` exist in `curriculum/api/TopicView.java` but are **not** sent.

**Nested: `PlanRequest.PrerequisiteEdge`** (line 139)

| Component | Type | Nullable |
|---|---|---|
| `prerequisiteTopicId` | `java.util.UUID` | yes (unchecked) — studied first |
| `dependentTopicId` | `java.util.UUID` | yes (unchecked) — depends on it |
| `strength` | `EdgeStrength` (`HARD` / `SOFT`) | yes (unchecked) |
| `provenance` | `EdgeProvenance` (`CURATED` / `TEXTBOOK_ORDER` / `DERIVED`) | yes (unchecked) |

The edge's own `id` and `sourceReference` exist in
`curriculum/api/PrerequisiteEdgeView.java` but are **not** sent.

**Nested: `PlanRequest.TopicHistory`** (line 155)

| Component | Type | Nullable |
|---|---|---|
| `topicId` | `java.util.UUID` | yes (unchecked) |
| `sessionCount` | `int` | n/a (primitive) — closed sessions with a recorded duration |
| `totalMinutes` | `long` | n/a (primitive) |
| `lastStudiedAt` | `java.time.Instant` | **yes, by contract** — Javadoc line 152: "or `null`" |
| `recallRatings` | `List<RecallRating>` | no — `List.copyOf` (line 164) — oldest first |

Compact constructor: lines 163-165, defensive copy of `recallRatings` only.

### 2.2 `PlanResponse` (record, 4 components)

| # | Component | Type | Nullable | Notes |
|---|---|---|---|---|
| 1 | `contractVersion` | `String` | yes (unchecked) | Must equal `"1.0"` — enforced later, in `validated` |
| 2 | `sessions` | `List<PlanResponse.ScheduledSession>` | **null-tolerant** — coerced to `List.of()` (line 31) | Then rejected if empty by `validated` |
| 3 | `fitness` | `Map<String, Object>` | **null-tolerant** — coerced to `Map.of()` (line 32) | Never interpreted by the backend |
| 4 | `metadata` | `PlanResponse.ExecutionMetadata` | yes (unchecked) | Null-checked in `validateMetadata` |

Compact constructor: lines 30-33. Unlike `PlanRequest`, it accepts null for `sessions` and
`fitness` and substitutes empty collections.

**Nested: `PlanResponse.ScheduledSession`** (line 44)

| Component | Type | Nullable |
|---|---|---|
| `topicId` | `java.util.UUID` | structurally yes; rejected by `validated` |
| `kind` | `SessionKind` (`STUDY` / `REVISION`) | structurally yes; rejected by `validated` |
| `scheduledStart` | `java.time.Instant` | structurally yes; rejected by `validated` |
| `durationMinutes` | `int` | n/a (primitive); must be `> 0` per `validated` |
| `sequenceIndex` | `int` | n/a (primitive); must be unique within the plan per `validated` |

**Nested: `PlanResponse.ExecutionMetadata`** (line 61)

| Component | Type | Nullable |
|---|---|---|
| `coreVersion` | `String` | structurally yes; rejected by `validated` if null or blank |
| `randomSeed` | `long` | n/a (primitive); must echo the seed that was sent |
| `generations` | `int` | n/a (primitive); **never validated and never persisted** |
| `elapsedMillis` | `long` | n/a (primitive); **never validated and never persisted** |

There is no `planId`, no per-session identifier, and no explanation or rationale field in the
response. `generations` and `elapsedMillis` are read from the wire and then dropped — nothing
in `GeneratedPlanWriter` stores them.

---

## 3. Invariants `validated` enforces on the Core's response

Eight checks, in execution order. All failures raise `CoreProtocolException` (never
`CoreUnavailableException`): per the class Javadoc at `RestSinapseCore.java:22-30`, an answer
the contract cannot read is not retryable. All file references are `RestSinapseCore.java`.

1. **The body is not empty.** — `line 97`: `if (response == null)` → `"the core answered with an empty body"` (line 98).
2. **The response's contract version equals this backend's.** — `line 100`: `if (!PlanRequest.VERSION.equals(response.contractVersion()))` → message naming both versions (lines 101-103).
3. **At least one session was produced.** — `line 105`: `if (response.sessions().isEmpty())` → `"the core produced no sessions"` (line 109).
4. **Execution metadata exists and names a core version.** — `line 118`: `if (metadata == null || metadata.coreVersion() == null || metadata.coreVersion().isBlank())` → `"the core did not say which version produced the plan"` (line 119).
5. **The seed echoed back is the seed that was sent.** — `line 121`: `if (metadata.randomSeed() != request.randomSeed())` → `"the core ran with a seed other than the one sent"` (line 122).
6. **Every session has a topic, a kind and a start instant.** — `lines 129-130`: `if (session.topicId() == null || session.kind() == null || session.scheduledStart() == null)` → `"the core returned an incomplete session"` (line 131).
7. **Every session has a positive duration.** — `line 133`: `if (session.durationMinutes() <= 0)` → `"the core returned a session of no length"` (line 134).
8. **No sequence index repeats within the plan.** — `line 136`: `if (!sequences.add(session.sequenceIndex()))` → `"the core repeated a sequence index"` (line 139).

**What `validated` does not check**, and a bridge should not assume it does:

- That `scheduledStart` falls inside the horizon, or inside any declared availability slot.
- That sessions do not overlap one another.
- That `topicId` is one of the topics that were sent.
- That `sequenceIndex` values are contiguous, zero-based, or ordered — only that they are distinct.
- That the response's `contractVersion` field is echoed by the request's, rather than simply equal to the constant.
- Anything at all about `fitness`, `generations` or `elapsedMillis`.

---

## 4. Does the platform already send each of these?

Against `SnapshotAssembler.assemble` (`planning/orchestration/SnapshotAssembler.java:94-130`)
and the nested records above.

| # | Item | Sent? | Field and evidence |
|---|---|---|---|
| 1 | Topics with UUID | **Yes** | `PlanRequest.Topic.id` (`PlanRequest.java:122`), populated at `SnapshotAssembler.java:190` from `catalog.topicsOfSubjects(subjectIds)` (line 121). `subjectId` travels too. |
| 2 | Prerequisite edges with `strength` and `provenance` | **Yes** | `PlanRequest.PrerequisiteEdge.strength` / `.provenance` (`PlanRequest.java:142-143`), mapped at `SnapshotAssembler.java:199-204` from `graph.edgesTouchingSubjects(subjectIds)` (line 124). Edges with one endpoint outside the planned subjects are deliberately kept (Javadoc lines 194-198). |
| 3 | `recallRatings` per topic | **Yes** | `PlanRequest.TopicHistory.recallRatings` (`PlanRequest.java:160`), accumulated in `TopicSummary.add` at `SnapshotAssembler.java:220-222`, oldest first (`sessions.reversed()`, line 175). Only non-null ratings are appended. |
| 4 | `lastStudiedAt` | **Yes** | `PlanRequest.TopicHistory.lastStudiedAt` (`PlanRequest.java:159`), set at `SnapshotAssembler.java:218` from `session.endedAt()`. Null when the topic has no closed session with a recorded duration. |
| 5 | `goals[].priority` | **Yes** | `PlanRequest.Goal.priority` (`PlanRequest.java:103`), copied at `SnapshotAssembler.java:184` from `StudyGoalView.priority()`. |
| 6 | `targetDate` | **Yes** | `PlanRequest.Goal.targetDate` (`PlanRequest.java:103`), copied at `SnapshotAssembler.java:184`. Nullable by contract, and explicitly *not* the horizon. |
| 7 | Availability as absolute instants | **Yes** | `PlanRequest.AvailabilitySlot(Instant start, Instant end)` (`PlanRequest.java:89`), expanded day by day at `SnapshotAssembler.java:148-163` and resolved through `accounts.timeZoneOf(...)` (line 141), then sorted by start (line 162). Empty intervals after zone resolution are dropped (line 157). |
| 8 | `estimatedMinutes` per topic | **Yes** | `PlanRequest.Topic.estimatedMinutes` (`PlanRequest.java:126`), computed at `SnapshotAssembler.java:188-189` as the configured band minutes times the per-student effort factor, floored at 1. The band itself travels alongside as `effortTier` (a `String`, line 191). |
| 9 | `algorithmParams` | **Yes** | `PlanRequest.algorithmParams` (`PlanRequest.java:46`), taken from configuration at `SnapshotAssembler.java:128` (`configuration.algorithmParams()`), defined at `application.yml:328-331`. Opaque to this side. |
| 10 | `randomSeed` | **Yes** | `PlanRequest.randomSeed` (`PlanRequest.java:47`), passed into `assemble` (line 94) and set at line 129. Drawn by `RandomSeedSource.next()` (`RandomSeedSource.java:21`) at `PlanGenerationOrchestrator.java:85`, and checked back against the response at `RestSinapseCore.java:121`. |

**All ten are sent today.** Two qualifications that matter for a bridge:

- `effortTier` crosses the wire as a `String` (`topic.effortTier().name()`,
  `SnapshotAssembler.java:191`), not as an enum declared in the contract package. There is no
  `EffortTier` type in `coreclient/contract/`. The receiving side has to accept the four
  literals `SHORT`, `STANDARD`, `LONG`, `EXTENDED` as free-form text.
- Items 3 and 4 are bounded by `sinapse.planning.generation.history-window`
  (`application.yml:324`, `90d`): `history` only covers sessions in that window
  (`SnapshotAssembler.java:106-107`). A topic studied 91 days ago contributes nothing to the
  snapshot.

---

## 5. Items marked NOT FOUND

| Item asked for | Status |
|---|---|
| Read model for `GET /study-plans/current` | **NOT FOUND** in the `readmodel` module. The route exists and is served by `planning/internal/web/StudyPlanController.java:71`; see §1.5. No substitute is offered here. |

Everything else requested was located and is reported above with its real path.
