# INTEGRATION — the Core as seen from sinapse-platform

This repository is the **consumer** of the interface; `exam-optimizer-application` (the Core,
https://github.com/gustavo-rm/exam-optimizer-application) is the provider. The provider's view is
`other-repo@40e6061:docs/ai/INTEGRATION.md`; this file does not restate it. Platform labels are
against `origin/main` at `e4c0be0`; Core references are to `origin/main` at `other-repo@40e6061`
(whose own labels are against `9104c1b`). Code paths are under
`src/main/java/br/com/sinapse/platform/`.

## How this repository calls the Core

| Item | Value | Status |
|---|---|---|
| URL | `sinapse.core.base-url` = `${SINAPSE_CORE_URL:http://localhost:8090}` + `sinapse.core.plan-path` = `/plans` | READ(application.yml:291-292@e4c0be0) |
| Client | `coreclient/internal/RestSinapseCore` (implements `coreclient/api/SinapseCore`), `RestClient` built in `CoreClientConfiguration` from the application's own builder and mapper | READ(RestSinapseCore.java:48-81; CoreClientConfiguration.java@e4c0be0) |
| Timeouts | connect `5s`, read `10m` (`SimpleClientHttpRequestFactory`) | READ(application.yml:293,297; CoreProperties.java@e4c0be0) |
| Retries | **none in the client.** The job retries: `max-attempts: 3`, exponential backoff from `30s`, only for `CORE_UNAVAILABLE` | READ(application.yml:317-318; PlanGenerationFailure.java:43-45; GenerationRequestService.java:163-166@e4c0be0) |
| Worker cycle | `poll-cron: '0/10 * * * * *'`, `batch-size: 1` | READ(application.yml:319-321@e4c0be0) |
| Rate limit on asking | 10 generation requests per hour per account | READ(application.yml:199-203@e4c0be0) |
| Credentials sent | none: body, `Content-Type`, `Accept` only | READ(RestSinapseCore.java:67-72@e4c0be0) |

Error mapping (the full table, with the reasoning, is [INTEGRACAO_CORE.md §2](../INTEGRACAO_CORE.md)):

- unreachable, timeout, any other `RestClientException`, or a non-2xx non-4xx status →
  `CoreUnavailableException` → job `CORE_UNAVAILABLE`, retried. READ(RestSinapseCore.java:73-79,91-94@e4c0be0)
- 4xx, unreadable body, or a response failing `validated` → `CoreProtocolException` → job
  `CORE_REJECTED`, not retried. READ(RestSinapseCore.java:87-90,95-99; PlanGenerationOrchestrator.java:94-100@e4c0be0)
- Mapping guarded by `RestSinapseCoreTest` (14 tests) and `CoreUnreachableIntegrationTest`.
  OBSERVED(e4c0be0, `./mvnw test -Dtest=RestSinapseCoreTest` → 14 tests, 0 failures); the
  integration test needs a database and was not run here.
- The client sees no RFC 7807 problem for a Core failure: `GET …/generation-requests/{id}` answers
  `200` with `status=FAILED` and `failureReason`. READ(PlanGenerationFailure.java@e4c0be0)

## Assumptions about the Core

"Verified by" names a test or command of **this** repository. A guard test (stub Core) proves
the platform's reaction, not the Core's behaviour; only `CoreEndToEndIntegrationTest` (profile
`core-e2e`, needs a running Core) exercises the real Core, and it was **not run in this task**.

| # | Assumption | Status | Verified by | Source in the Core |
|---|---|---|---|---|
| A1 | `POST /plans` exists, but only with Core profile `baseline-core` | READ | none here; E2E needs it running (not run) | other-repo@40e6061:docs/ai/INTEGRATION.md "Provided interface" |
| A2 | Wire shape is contract `1.0`; both reference JSON files are byte-identical in the two repositories | OBSERVED for byte identity | `sha256sum src/test/resources/contract/*.json` on both clones@e4c0be0/40e6061 (equal); shape vs records: `CoreContractGoldenTest` (needs DB, not run) | other-repo@40e6061:src/test/resources/contract/ |
| A3 | Same body + same `randomSeed` + same engine ⇒ same plan | READ | `CoreEndToEndIntegrationTest.aRequestedPlanComesBackFromTheRealCoreAndTheSameSeedReproducesIt` (core-e2e, not run) | other-repo@40e6061:docs/ai/INTEGRATION.md "Determinism and reproducibility" |
| A4 | The Core echoes the seed and states `contractVersion` and `coreVersion` | READ | guard: `RestSinapseCoreTest` seed/version cases. OBSERVED(e4c0be0) for the guard only | other-repo@40e6061:docs/ai/INTEGRATION.md "Determinism and reproducibility" |
| A5 | Every session fits one window, sessions do not overlap, topics were sent, `sequenceIndex` is contiguous | READ | guard re-checks all four: `CoreResponseInvariantsTest`, OBSERVED(e4c0be0, 11 tests, 0 failures) | other-repo@40e6061:docs/ai/INTEGRATION.md "Error types" (a plan breaking its own invariants is a 500) |
| A6 | Sessions start inside the horizon and `HARD` prerequisites come first | READ | **no guard**: the platform does not check either (see Response validation); E2E checks both on the stored plan (not run) | as A5 |
| A7 | A 4xx is deterministic for the same payload, so not worth retrying | READ | guard: `RestSinapseCoreTest.aRejectionByTheCoreIsAProtocolFailureAndNotWorthRetrying`, OBSERVED(e4c0be0) | other-repo@40e6061:docs/ai/INTEGRATION.md "Error types" |
| A8 | A 5xx is transient, so worth retrying | **contradicted**, READ | none; the Core's `500 plan-invariant-violation` is a defect and burns 3 attempts (STATE.md K5) | other-repo@40e6061:docs/ai/INTEGRATION.md "Error types" |
| A9 | `generations`, `population-size`, `mutation-rate` in `algorithmParams` take effect | **contradicted**, READ in the Core, not executed anywhere | none | other-repo@40e6061:docs/ai/INTEGRATION.md "algorithmParams — index"; other-repo@40e6061:docs/ai/STATE.md K6 |
| A10 | `algorithmParams.engine` absent → `greedy-baseline` | READ | none here | other-repo@40e6061:docs/ai/INTEGRATION.md "engine over HTTP" |
| A11 | `metadata.elapsedMillis` measures the run | **contradicted**, READ: constant 0 in every engine | none | other-repo@40e6061:docs/ai/STATE.md K5 |
| A12 | `metadata.coreVersion` identifies what ran (ADR 0007) | **contradicted**, READ: Maven version, unchanged across many commits | none | other-repo@40e6061:docs/ai/STATE.md K7 |
| A13 | The GA engines plan the real 35-topic catalogue | **contradicted**, READ: `ga` and `ga-timeline` refuse most real-catalogue instances | none here; the Core's diagnosis used this repository's instances | other-repo@40e6061:docs/ai/STATE.md K2, K3 |
| A14 | Not enough availability yields a declared partial plan (`fitness.partial`), not an error | READ | none; the platform stores a non-empty partial plan as `ACTIVE` without reading `partial` | other-repo@40e6061:docs/ai/INTEGRATION.md "Known deviations" |
| A15 | `fitness` is an open map whose keys the platform must not compile in | READ | `FitnessTermNamesAreNotCompiledTest` (not run here) | other-repo@40e6061:docs/ai/INTEGRATION.md "Effective values in the response" |
| A16 | `/plans` has no authentication; the network must be private | READ | none (deployment) | other-repo@40e6061:docs/ai/INTEGRATION.md "Provided interface" |

## Response validation

`RestSinapseCore.validated` (`RestSinapseCore.java:113-135@e4c0be0`) applies **twelve** checks
today; the per-check line references and messages are in
[CORE_CONTRACT_SURVEY.md §3](../CORE_CONTRACT_SURVEY.md), which matches the code. READ@e4c0be0

Enforced: (1) body present; (2) `contractVersion` = `1.0`; (3) at least one session;
(4) `coreVersion` present; (5) seed echoed; (6) session has topic, kind, start; (7) duration > 0;
(8) no repeated `sequenceIndex`; (9) topic among those sent; (10) whole session inside one window,
adjacent windows not merged; (11) no overlap, half-open; (12) `sequenceIndex` contiguous.
Tests: 1–8 `RestSinapseCoreTest`; 9–12 `CoreResponseInvariantsTest` and
`planning/orchestration/CoreAnswerBreakingAnInvariantIntegrationTest`. OBSERVED(e4c0be0) for the
two unit classes (25 tests, 0 failures); the integration test was not run (needs DB).

Not enforced (READ, `RestSinapseCore.java:102-112` and CORE_CONTRACT_SURVEY.md §3): session start
inside the horizon (deliberate: horizon is `LocalDate`, sessions are `Instant`, the contract names
no zone); `HARD` prerequisite order; `sequenceIndex` starting at 0; sequence order = chronological
order; sessions not in the past; anything about `fitness`, `generations`, `elapsedMillis`, `partial`.

## What is sent in algorithmParams and what is persisted

- Sent today: `population-size: 120`, `generations: 400`, `mutation-rate: 0.05`.
  READ(application.yml:328-331@e4c0be0)
- Path: `sinapse.planning.generation.algorithm-params` (an open `Map<String,Object>`,
  `PlanningProperties.java:88`) → `SnapshotAssembler.java:128` → `PlanRequest.algorithmParams`.
  READ@e4c0be0
- `engine` is not in the default configuration, so a default deployment never sends it and the
  Core runs `greedy-baseline`. Because the map is open, a deployment can add it
  (`SINAPSE_PLANNING_GENERATION_ALGORITHMPARAMS_ENGINE`); the E2E test does exactly that
  (`CoreEndToEndIntegrationTest.java:113-114`). READ@e4c0be0. A deployment sending `ga` this way
  was executed by the 2026-10-05 audit: UNVERIFIED([PRONTIDAO_INTEGRACAO_AG.md](../PRONTIDAO_INTEGRACAO_AG.md) B6, at 928eb56).
- Persisted: the same map, before the call, in `plan_generation_request.algorithm_params`
  (`V5__planning.sql:76`), with the snapshot and the seed
  (`PlanGenerationOrchestrator.java:85-87`). READ@e4c0be0. The three GA keys are recorded as
  if they ran; per A9 they do not (STATE.md K3).
- Persisted from the response: `core_version`, `generations`, `elapsed_millis` on the job and
  `fitness` whole on the plan (`GeneratedPlanWriter.java:61`). READ@e4c0be0
- The seed is drawn again on every attempt (`PlanGenerationOrchestrator.java:85`). READ@e4c0be0

## Reference contract JSON

- Here: `src/test/resources/contract/plan-request-v1.0.json`, `plan-response-v1.0.json`.
  In the Core: same paths. Rules: ADR 0015 (espelhamento) and ADR 0016.
- Identity rule: byte for byte. OBSERVED(e4c0be0 / other-repo@40e6061, `sha256sum`: request
  `2cfaf210…`, response `528fcd60…` on both sides).
- Protection here: `coreclient/CoreContractGoldenTest` (round trip over the union of keys plus a
  record-component sweep, with the application's mapper). Nothing in either build compares the
  two repositories' copies: the identity is kept by process. READ@e4c0be0
- The Core also keeps `contract/openapi-snapshot.json`; this repository has no OpenAPI snapshot
  (STATE.md K8). OBSERVED(`ls` on both clones)

## Core versions this repository was verified against

- Read for this document: `other-repo@40e6061` (no execution).
- Last execution against a real Core recorded in this repository: Core `61233ad`, `coreVersion`
  `2.0.1`, by the 2026-10-05 audit. UNVERIFIED([PRONTIDAO_INTEGRACAO_AG.md §2](../PRONTIDAO_INTEGRACAO_AG.md))

## Contract change protocol

The provider owns it: `other-repo@40e6061:docs/ai/INTEGRATION.md` "Contract change protocol"
(itself a PROPOSAL there). This repository's side of it is ADR 0015 and ADR 0016. Not repeated here.
