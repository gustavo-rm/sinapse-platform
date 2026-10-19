# STATE — sinapse-platform

Reviewed: 2026-10-08 UTC at `e4c0be0` (origin/main). Core read at `other-repo@40e6061`.

Present only; history is `git log` and the PR descriptions. Edit only your own row in **Work**.
Task IDs come from the project owner's planning document, which lives outside this repository;
this table is now the source of their status. What that document says about each ID, and which
finding each ID was meant to cover, is UNVERIFIED(planner, 2026-10-08) unless a row says otherwise.

## Work

| ID | Title | Status | PR | Depends on | Notes |
|---|---|---|---|---|---|
| SP-A | (planner's ID; scope not in this repository) | done | unknown | — | Merged per the planner: UNVERIFIED(planner, 2026-10-08). No PR in `git log --merges` names it |
| SP-6 | Core response validation | done | #20 (merge `e4c0be0`) | — | Topic, whole-window, overlap, contiguity enforced and tested (INTEGRATION.md "Response validation"). Horizon deliberately not checked (`RestSinapseCore.java:105-111`): whether that closes SP-6 is the owner's call. READ@e4c0be0; tests OBSERVED(e4c0be0) |
| SP-7 | Catalogue read routes and importer from the jar | done | #17 (merge `2956e0f`) | — | `GET /api/v1/subjects`, `/subjects/{id}/topics` (`curriculum/internal/web/CatalogController.java`); `CatalogRunner` gets `@Autowired` (`CatalogRunner.java:59`). READ@e4c0be0; docs: [CONTRATO_API_SINAPSE.md §7](../CONTRATO_API_SINAPSE.md), [CARGA_DO_CATALOGO.md](../CARGA_DO_CATALOGO.md) |
| SP-11 | Local account activation | done | #19 (merge `ef15b18`) | — | `DevOnlyLoggingAccountNotifier`, `local` profile only, fails closed. Same PR adds the empty-`trusted-proxies` startup warning. READ@e4c0be0; doc: [DESENVOLVIMENTO_LOCAL.md](../DESENVOLVIMENTO_LOCAL.md) |
| SP-10 | Orphaned `RUNNING` job recovery and one seed per job | done on merge | #22 | — | Closes K1, K2. Bound = core connect + read timeout + `orphan-margin` (2m); one conditional UPDATE per worker pass; no migration, no new `failureReason`. Detail: [INTEGRACAO_CORE.md §3.1–3.2](../INTEGRACAO_CORE.md). READ@12a384e; tests OBSERVED(12a384e, `OrphanedJobRecovery*`, `OrphanBound*`, `SeedPerJob*`) |
| SP-8 | Engine by configuration; item 3b honest `algorithmParams`; item 3c `core_call_millis` | done on merge | #23 | item 3b: owner decision SP-8-3b | Engine by YAML, `SPRING_APPLICATION_JSON` or env var, validated at startup; `core_call_millis` (V9). Item 3b **deferred**: K18. Read Core at other-repo@3b54ba4. OBSERVED(`./mvnw verify` with external PostgreSQL); core-e2e not run (no Core provided). Detail: [INTEGRACAO_CORE.md §1](../INTEGRACAO_CORE.md) |
| SP-13 | Versioned OpenAPI snapshot | done on merge | #24 | — | `docs/openapi/openapi.json`, 59 operations, no profile active; `OpenApiSnapshotIntegrationTest` compares and checks two starts. Closes K8; findings K19–K21. Flow: [docs/openapi/README.md](../openapi/README.md) |
| SP-12 | Documentation reconciliation | pending | — | — | K10, K11, K12 |
| SP-9 | Coverage floor (optional) | pending | — | — | K9 |
| AUD-2 | Re-audit | pending | — | the SP rows above | Last audit: [PRONTIDAO_INTEGRACAO_AG.md](../PRONTIDAO_INTEGRACAO_AG.md), at `928eb56` |
| AI-1 | AI context layer (`docs/ai/`, CLAUDE.md protocol) | done | #21 (merge `de38505`) | Core CTX-1 (merged, `other-repo@40e6061`) | ID assigned here; the planner had none |

The planner lists SP-8, SP-13, SP-12, SP-9 and AUD-2 as running in series; that order is
UNVERIFIED(planner, 2026-10-08).

## Open decisions

Owner of every row: project owner. The platform's own undecided questions (J1–J3, D4 retention
measurement, D5, P2, P3) are in [CLAUDE.md §7](../../CLAUDE.md) and are not repeated.
**Name clash:** CLAUDE.md §7 "D4" (objective retention measurement) is not the Core's D4 below.

| ID | Question | Blocks here | Options | Recommendation |
|---|---|---|---|---|
| SP-8-3b | How to unblock SP-8 item 3b (Core D4 is decided, CONTEXT.md): regenerate the three real-catalogue instances without the three GA keys and announce them (Core EOA-H2), or pin the three keys in the instance generator as a historical fixture? Both edit tests | SP-8 item 3b | regenerate + announce; pin in generator | regenerate + announce — PROPOSAL |
| K17-D | May a student route show `fitness.engine` and `fitness.build`? | nothing | keep (today); filter the keys (needs an exception to `FitnessTermNamesAreNotCompiledTest`) | none: owner's call |
| Core D1, D2, D3 | Repair criteria, repair scope, what the student sees when topics do not fit | nothing here directly; D1/D2 gate the Core's EOA-12, which SP-H3 waits on | see other-repo@40e6061:docs/ai/STATE.md | defined there |
| D-AI1 | Record the two owner decisions in CONTEXT.md "Decisions recorded here" as ADRs in `docs/adr/`? | nothing | ADR 0017+ in the existing Portuguese format; leave as DECISION lines | ADRs — PROPOSAL |

## Known issues and limitations

| ID | Description | Status | Evidence | Workaround | Resolved by |
|---|---|---|---|---|---|
| K1 | A job left `RUNNING` by a dead worker stayed `RUNNING` forever and the account got `409` | closed by SP-10: each worker pass requeues or fails it once it is older than the longest attempt | `OrphanedJobRecovery.java`, `PlanGenerationRequestRepository.recoverOrphans`@f7d69eb; `OrphanedJobRecoveryIntegrationTest` OBSERVED(12a384e) | — | SP-10 |
| K2 | Each retry drew a new seed and overwrote the previous attempt's seed | closed by SP-10: one seed per job. The snapshot is still reassembled and overwritten per attempt, deliberately ([INTEGRACAO_CORE.md §3.1](../INTEGRACAO_CORE.md)) | `PlanGenerationOrchestrator.seedOf`@12a384e; `SeedPerJobIntegrationTest` OBSERVED(12a384e) | — | SP-10 |
| K3 | `algorithm_params` records `generations`/`population-size`/`mutation-rate` that the Core ignores; it records what was **sent**, not applied (ADR 0007 note of 2026-10-19) | READ here; Core side executed (other-repo@3b54ba4:docs/ai/STATE.md K6, `HiperparametrosDoCoreHttpTest`) | INTEGRATION.md A9 | read `study_plan.fitness` (`engine`, `build`) and `generations` for what ran | SP-8 item 3b, blocked by K18 |
| K4 | `elapsed_millis` is always 0: reserved by the Core (DT-2), kept as reported, measures nothing. The platform now measures call latency in `core_call_millis` (NULL for jobs before V9 and for failed jobs). Latency is not engine cost | OBSERVED(`CoreCallMillisIntegrationTest`, `CoreCallTimingTest`) | INTEGRACAO_CORE.md §1.2 | engine cost: Core harness (EOA-M, EOA-9b) | SP-8 item 3c (this PR) |
| K5 | A Core `500 plan-invariant-violation` is treated as unavailability and retried 3 times | READ | `RestSinapseCore.java:91-94@e4c0be0`; INTEGRATION.md A8 | none | unassigned |
| K6 | `trusted-proxies` is empty by default; behind the reverse proxy every anonymous limit becomes global | READ; mitigated: startup warning outside `local`/`test` | `application.yml:106`, `shared/ratelimit/TrustedProxiesStartupCheck.java@e4c0be0` | set it per deployment: [PROXY_REVERSO.md](../PROXY_REVERSO.md) | deployment configuration |
| K7 | Horizon: Core reads it in UTC, platform expands windows by days in the account's zone; the platform does not check session-in-horizon | READ; effect (silent loss of availability at the edges) is a HYPOTHESIS | [CORE_CONTRACT_SURVEY.md §3](../CORE_CONTRACT_SURVEY.md); PRONTIDAO X7 | none | unassigned |
| K8 | No versioned OpenAPI snapshot | closed by SP-13: `docs/openapi/openapi.json`, compared by `OpenApiSnapshotIntegrationTest` | OBSERVED(`./mvnw verify`) | — | SP-13 |
| K9 | No JaCoCo floor; CLAUDE.md §8 "no decrease" is not enforced | READ | `pom.xml:191-210@e4c0be0` | compare `target/site/jacoco` by hand | SP-9 |
| K10 | CLAUDE.md §3 layout omits `curation`, `datarights`, `readmodel` | READ | CLAUDE.md §3 vs `src/main/java` tree@e4c0be0 | CONTEXT.md "Architecture" | SP-12 |
| K11 | Two ADRs numbered 0015 (`espelhamento`, `ponte`) | READ (`ls docs/adr`@e4c0be0) | — | cite by full file name | SP-12 |
| K12 | Team docs disagree with code: `PlanGenerationRequest.java:21-23` and `PlanGenerationRequestRepository.java:16-19` Javadoc say the job is not executed; CORE_CONTRACT_SURVEY.md:342-345 says `generations`/`elapsedMillis` are never persisted (they are, since SP-3); PRONTIDAO A2c/E1/X1/X2 describe `928eb56`, since fixed by #17, #19, #20 | READ@e4c0be0. The engine list (INTEGRACAO_CORE.md §1) and ADR 0007's `algorithm_params` wording were fixed in SP-8 | — | trust code, then this layer | SP-12 |
| K13 | `ga` refuses most of the real catalogue (23/30 runs); `ga-timeline` was fixed by the Core's EOA-12 (30/30 non-empty, valid) | READ in the Core (other-repo@3b54ba4:docs/ai/STATE.md K2, K3), not executed here | INTEGRATION.md A13 | default engine | SP-H3 |
| K14 | `POST …/generation-requests` answers `201` without `Location`; `progress` is always absent | READ (`GenerationRequestController.java:84`, `GenerationRequestService.java:244-245@e4c0be0`); by decision F4 | — | build the poll URL from `id` | deliberate |
| K15 | Generation request rate limit 10/h per account; worker poll 10 s dominates time to `READY` | READ (`application.yml:199-203,321@e4c0be0`) | — | — | deliberate |
| K17 | The effective engine and Core build reach student routes: `fitness` is passed through whole on `study-plans` and summary routes, and the Core puts `engine` and `build` in it | READ (`StudyPlanResponse.java`, `PlanSummaryView.java`); pinned by `FitnessReportIntegrationTest` | — | — | owner decision K17-D |
| K18 | SP-8 item 3b cannot remove the three GA keys from `application.yml` without editing tests: the shared real-catalogue instances carry them, `RealCatalogInstanceSupport.productionAlgorithmParams` asserts the YAML declares parameters, and `RealCatalogInstancesTest` asserts a non-empty map and byte equality with a regeneration | READ (`RealCatalogInstanceSupport.java:166-186`, `RealCatalogInstancesTest.java:61,152`) | — | none; the keys stay sent and are recorded as sent | owner decision SP-8-3b |
| K19 | 15 `operationId`s carry springdoc's numeric dedup suffix (`list_1`…`list_7`, `open_1`, …), assigned in discovery order: a new method with the same name can renumber them and break a generated client | OBSERVED(`docs/openapi/openapi.json`) | review `operationId` changes in the snapshot diff | unassigned (`@Operation(operationId)` per route) |
| K20 | Error bodies: all 90 declared error responses are `application/problem+json` → `ProblemDetail`, but that schema documents a `properties` member absent from the wire and omits the `errors` extension (`ProblemDetails.java:60`); 9 GET operations declare no error response | OBSERVED(`docs/openapi/openapi.json`) | [CONTRATO_API_SINAPSE.md](../CONTRATO_API_SINAPSE.md) for the error contract | unassigned |
| K21 | `/api-docs`, `/api-docs/swagger-config` and `/swagger-ui/index.html` answer 200 to anonymous callers with no profile active (fallback `permitAll`, `SecurityConfiguration.java:138`); no `securitySchemes` declared | OBSERVED(MockMvc, no profile, disposable copy) | the reverse proxy decides whether they are published | owner decision (security) |
| K22 | The integration suite keeps up to 10 Spring contexts cached against PostgreSQL's default 100 connections (3 reserved); at Hikari's default of 10 per pool one more context failed with `too many clients already` | closed by SP-13: the test profile caps each pool at 5 (`application-test.yml`) | OBSERVED(`./mvnw verify`: 14 pools started, 4 shut down; the failure seen in SP-8 and SP-13 before the cap) | — | SP-13 |
| K16 | A legitimate attempt that outlives the orphan bound (margin too small for the environment, clock skew between instances) is requeued while still running: the Core is called twice for one job; the unique `study_plan.generation_request_id` keeps it to one plan and the losing write fails into the log | HYPOTHESIS, untested | [INTEGRACAO_CORE.md §3.2](../INTEGRACAO_CORE.md) | raise `sinapse.planning.generation.orphan-margin` | unassigned |

## Unknowns

- Current full-suite test count and coverage: the build was not run in full here (needs a
  database). The audit's 609 tests and 95.1 % instruction / 80.6 % branch coverage were at
  `928eb56`: UNVERIFIED([PRONTIDAO_INTEGRACAO_AG.md §2](../PRONTIDAO_INTEGRACAO_AG.md)).
- Whether `CoreEndToEndIntegrationTest` still passes against the Core at `other-repo@40e6061`:
  not run.
- Whether the three engines pass the core-e2e tests against a real Core: written in SP-8, not run
  (no Core was provided, and the protocol forbids running the Core's code from a clone).
- The scope of SP-A, and which audit findings SP-10 covers. UNVERIFIED(planner, 2026-10-08)
- The frontend's state (Angular; teacher area planned): it lives outside this repository. The
  backend already has teacher routes (`educational`, `readmodel` classroom reads), and nothing
  grants the `TEACHER` role (CLAUDE.md §7 P2). READ@e4c0be0
